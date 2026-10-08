package com.example.util

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.database.AppDatabase
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupFileInfo(
    val fileName: String,
    val file: File,
    val backupDate: String,
    val appVersion: String,
    val dbVersion: Int,
    val fileSizeFormatted: String,
    val clientCount: Int,
    val invoiceCount: Int,
    val paymentCount: Int,
    val installmentCount: Int,
    val timestamp: Long,
    val isDriveCloud: Boolean = false,
    val driveFileId: String? = null
)

data class GoogleDriveBackupResult(
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val timestamp: Long,
    val accountEmail: String,
    val message: String
)

class GoogleDriveBackupManager(private val context: Context) {

    companion object {
        private const val TAG = "GoogleDriveBackup"
        private const val PREFS_NAME = "drive_sync_prefs"
        private const val KEY_IS_SYNC_ENABLED = "is_sync_enabled"
        private const val KEY_DRIVE_ACCOUNT_EMAIL = "drive_account_email"
        private const val KEY_LAST_BACKUP_TIME = "last_drive_backup_time"
        private const val KEY_LAST_FILE_ID = "last_drive_file_id"
        private const val KEY_LAST_FILE_NAME = "last_drive_file_name"
        private const val KEY_LAST_FILE_SIZE = "last_drive_file_size"

        val SCOPE_DRIVE_FILE = Scope("https://www.googleapis.com/auth/drive.file")
        const val OAUTH_SCOPE_STRING = "oauth2:https://www.googleapis.com/auth/drive.file"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val apiClient = GoogleDriveApiClient(context)

    val driveFolder: File
        get() = File(context.filesDir, "Accountant_Backup").apply { if (!exists()) mkdirs() }

    val latestBackupJsonFile: File
        get() = File(driveFolder, "backup_latest.json")

    /**
     * Builds standard GoogleSignInClient configured with required Drive scopes.
     */
    fun getGoogleSignInClient(activityContext: Context = context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(SCOPE_DRIVE_FILE)
            .build()
        return GoogleSignIn.getClient(activityContext, gso)
    }

    fun getLastSignedInAccount(): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return if (account != null && GoogleSignIn.hasPermissions(account, SCOPE_DRIVE_FILE)) {
            account
        } else {
            null
        }
    }

    fun getSavedAccountEmail(): String? {
        return prefs.getString(KEY_DRIVE_ACCOUNT_EMAIL, null)
            ?: getLastSignedInAccount()?.email
    }

    fun isSyncEnabled(): Boolean {
        return prefs.getBoolean(KEY_IS_SYNC_ENABLED, false)
    }

    fun setSyncEnabled(enabled: Boolean, email: String?) {
        prefs.edit()
            .putBoolean(KEY_IS_SYNC_ENABLED, enabled)
            .putString(KEY_DRIVE_ACCOUNT_EMAIL, email)
            .apply()
    }

    /**
     * Obtains a valid OAuth 2.0 Access Token securely without logging it.
     */
    suspend fun getAccessToken(accountEmail: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val email = accountEmail
            ?: getSavedAccountEmail()
            ?: return@withContext Result.failure(IOException("يرجى تسجيل الدخول بحساب Google أولاً للمزامنة"))

        if (email.isBlank()) {
            return@withContext Result.failure(IOException("يرجى تسجيل الدخول بحساب Google أولاً للمزامنة"))
        }

        try {
            val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
            val backingAccount = lastAccount?.account
            
            val matchingAccount = if (backingAccount != null && backingAccount.name.equals(email, ignoreCase = true)) {
                backingAccount
            } else {
                val am = android.accounts.AccountManager.get(context)
                val googleAccounts = try { am.getAccountsByType("com.google") } catch (e: Throwable) { emptyArray() }
                googleAccounts.find { it.name.equals(email, ignoreCase = true) } ?: backingAccount ?: googleAccounts.firstOrNull()
            }

            if (matchingAccount == null) {
                return@withContext Result.failure(IOException("حساب Google ($email) غير مسجل على هذا الجهاز. يرجى تسجيل الدخول من شاشة النسخ الاحتياطي."))
            }

            val token = GoogleAuthUtil.getToken(context, matchingAccount, OAUTH_SCOPE_STRING)
            if (token.isNullOrBlank()) {
                return@withContext Result.failure(IOException("تعذر الحصول على تصريح Google Drive"))
            }
            Result.success(token)
        } catch (recoverable: UserRecoverableAuthException) {
            Result.failure(recoverable)
        } catch (e: Throwable) {
            Result.failure(Exception(e.localizedMessage ?: "حدث خطأ أثناء الاتصال بحساب Google"))
        }
    }

    /**
     * Clears cached token if invalid or expired.
     */
    fun invalidateToken(token: String) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (_: Exception) {}
    }

    /**
     * Full, robust workflow:
     * 1. Validates Network
     * 2. Obtains OAuth 2.0 Access Token
     * 3. Creates local structured ZIP backup with Database & Images (BackupEngine)
     * 4. Finds or creates "الحكيمي للأدوية والمستلزمات الطبية / Backups" folder on Google Drive
     * 5. Uploads ZIP file via stream-based multipart upload
     * 6. Verifies response from Google Drive
     * 7. Saves metadata locally
     */
    suspend fun createAndUploadBackup(
        db: AppDatabase,
        accountEmail: String? = null,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!apiClient.isNetworkAvailable()) {
            return@withContext Result.failure(IOException("لا يوجد اتصال بالإنترنت. تم الاحتفاظ بالنسخة محليًا ويمكنك المزامنة لاحقًا."))
        }

        try {
            onProgress(10, "🔐 جاري التحقق من جلسة وصلاحية حساب Google...")

            // 1. Get Access Token
            var tokenResult = getAccessToken(accountEmail)
            if (tokenResult.isFailure) {
                val err = tokenResult.exceptionOrNull()
                if (err is UserRecoverableAuthException) {
                    return@withContext Result.failure(err)
                }
                return@withContext Result.failure(IOException("تعذر الحصول على تصريح Google Drive: ${err?.localizedMessage ?: "يرجى تسجيل الدخول"}"))
            }

            var token = tokenResult.getOrThrow()

            // 2. Create structured local ZIP backup with images & manifest
            onProgress(25, "📦 جاري إنشاء النسخة الاحتياطية وضغط البيانات والصور...")
            val backupResult = BackupEngine.createBackup(
                context = context,
                db = db,
                destination = "Google Drive",
                onProgress = { percent, stage ->
                    val mapped = 25 + (percent * 0.25).toInt()
                    onProgress(mapped, stage)
                }
            )

            if (backupResult.isFailure) {
                return@withContext Result.failure(
                    backupResult.exceptionOrNull() ?: IOException("فشل في تجهيز ملف النسخة الاحتياطية محلياً")
                )
            }

            val zipFile = backupResult.getOrThrow()

            // 3. Locate or create app Backups folder in Drive
            onProgress(55, "📁 جاري التحقق من مجلد الحكيمي في Google Drive...")
            var folderResult = apiClient.getOrCreateAppBackupsFolder(token)
            if (folderResult.isFailure && folderResult.exceptionOrNull()?.message == "UNAUTHORIZED") {
                // Invalidate expired token and retry once
                invalidateToken(token)
                val refreshedTokenResult = getAccessToken(accountEmail)
                if (refreshedTokenResult.isSuccess) {
                    token = refreshedTokenResult.getOrThrow()
                    folderResult = apiClient.getOrCreateAppBackupsFolder(token)
                }
            }

            if (folderResult.isFailure) {
                val ex = folderResult.exceptionOrNull()
                return@withContext Result.failure(
                    IOException("تعذر إنشاء أو فتح مجلد النسخ الاحتياطية في Google Drive: ${ex?.message ?: ""}")
                )
            }

            val backupsFolderId = folderResult.getOrThrow()

            // 4. Upload the ZIP file to Google Drive
            onProgress(70, "☁️ جاري رفع وحفظ النسخة الاحتياطية إلى Google Drive...")
            var uploadResult = apiClient.uploadBackupFile(
                accessToken = token,
                file = zipFile,
                parentFolderId = backupsFolderId,
                onProgress = onProgress
            )

            if (uploadResult.isFailure && uploadResult.exceptionOrNull()?.message == "UNAUTHORIZED") {
                invalidateToken(token)
                val refreshedTokenResult = getAccessToken(accountEmail)
                if (refreshedTokenResult.isSuccess) {
                    token = refreshedTokenResult.getOrThrow()
                    uploadResult = apiClient.uploadBackupFile(
                        accessToken = token,
                        file = zipFile,
                        parentFolderId = backupsFolderId,
                        onProgress = onProgress
                    )
                }
            }

            if (uploadResult.isFailure) {
                val err = uploadResult.exceptionOrNull()
                val userMsg = when (err?.message) {
                    "QUOTA_EXCEEDED" -> "مساحة التخزين في حساب Google Drive ممتلئة. يرجى تحرير بعض المساحة."
                    "INSUFFICIENT_PERMISSIONS" -> "لم يتم منح الصلاحيات الكافية للوصول إلى Google Drive."
                    "NO_INTERNET" -> "انقطع الاتصال بالإنترنت أثناء الرفع. النسخة محفوظة محلياً."
                    else -> "تعذر رفع النسخة إلى Google Drive: ${err?.localizedMessage ?: "خطأ غير متوقع"}"
                }
                return@withContext Result.failure(IOException(userMsg))
            }

            val driveMetadata = uploadResult.getOrThrow()

            // 5. Update preferences with successful upload details
            val now = System.currentTimeMillis()
            val effectiveEmail = accountEmail ?: getSavedAccountEmail() ?: ""
            prefs.edit()
                .putBoolean(KEY_IS_SYNC_ENABLED, true)
                .putString(KEY_DRIVE_ACCOUNT_EMAIL, effectiveEmail)
                .putLong(KEY_LAST_BACKUP_TIME, now)
                .putString(KEY_LAST_FILE_ID, driveMetadata.id)
                .putString(KEY_LAST_FILE_NAME, driveMetadata.name)
                .putLong(KEY_LAST_FILE_SIZE, driveMetadata.size)
                .apply()

            val sizeKb = driveMetadata.size / 1024
            val successMsg = "تمت مزامنة النسخة الاحتياطية بنجاح إلى Google Drive!\nاسم الملف: ${driveMetadata.name} ($sizeKb KB)\nالحساب: $effectiveEmail"
            onProgress(100, "✓ اكتملت المزامنة السحابية بنجاح!")

            Result.success(successMsg)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lists backup files saved in Google Drive.
     */
    suspend fun listBackupsFromDrive(accountEmail: String? = null): Result<List<GoogleDriveFileMetadata>> = withContext(Dispatchers.IO) {
        try {
            val tokenResult = getAccessToken(accountEmail)
            if (tokenResult.isFailure) {
                return@withContext Result.failure(tokenResult.exceptionOrNull() ?: IOException("غير مسجل"))
            }

            var token = tokenResult.getOrThrow()
            var folderResult = apiClient.getOrCreateAppBackupsFolder(token)
            if (folderResult.isFailure && folderResult.exceptionOrNull()?.message == "UNAUTHORIZED") {
                invalidateToken(token)
                val refreshed = getAccessToken(accountEmail)
                if (refreshed.isSuccess) {
                    token = refreshed.getOrThrow()
                    folderResult = apiClient.getOrCreateAppBackupsFolder(token)
                }
            }

            if (folderResult.isFailure) {
                return@withContext Result.failure(folderResult.exceptionOrNull() ?: IOException("تعذر الوصول للمجلد"))
            }

            val folderId = folderResult.getOrThrow()
            apiClient.listBackups(token, folderId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads and restores a backup from Google Drive.
     */
    suspend fun restoreFromDriveFile(
        db: AppDatabase,
        fileId: String,
        fileName: String,
        accountEmail: String? = null,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!apiClient.isNetworkAvailable()) {
            return@withContext Result.failure(IOException("لا يوجد اتصال بالإنترنت لتنزيل النسخة من Google Drive"))
        }

        val tempFile = File(context.cacheDir, "temp_drive_restore_${System.currentTimeMillis()}.zip")
        try {
            onProgress(15, "🔐 جاري الاتصال بخدمة Google Drive...")
            var tokenResult = getAccessToken(accountEmail)
            if (tokenResult.isFailure) {
                return@withContext Result.failure(tokenResult.exceptionOrNull() ?: IOException("غير مصرح"))
            }

            var token = tokenResult.getOrThrow()
            var downloadResult = apiClient.downloadFile(
                accessToken = token,
                fileId = fileId,
                destinationFile = tempFile,
                onProgress = { p, s -> onProgress(p, s) }
            )

            if (downloadResult.isFailure && downloadResult.exceptionOrNull()?.message == "UNAUTHORIZED") {
                invalidateToken(token)
                val refreshed = getAccessToken(accountEmail)
                if (refreshed.isSuccess) {
                    token = refreshed.getOrThrow()
                    downloadResult = apiClient.downloadFile(
                        accessToken = token,
                        fileId = fileId,
                        destinationFile = tempFile,
                        onProgress = { p, s -> onProgress(p, s) }
                    )
                }
            }

            if (downloadResult.isFailure) {
                return@withContext Result.failure(downloadResult.exceptionOrNull() ?: IOException("فشل تنزيل الملف"))
            }

            onProgress(65, "📦 جاري فحص واستعادة محتويات النسخة وقاعدة البيانات...")
            val restoreResult = RestoreEngine.restoreFromFile(
                context = context,
                db = db,
                backupFile = tempFile,
                onProgress = { p, s ->
                    val mapped = 65 + (p * 0.35).toInt().coerceIn(0, 35)
                    onProgress(mapped, s)
                }
            )

            if (!restoreResult.success) {
                return@withContext Result.failure(IOException(restoreResult.message))
            }

            onProgress(100, "✓ اكتملت الاستعادة بنجاح!")
            Result.success("تمت استعادة البيانات بنجاح من النسخة السحابية ($fileName)!\n${restoreResult.message}")
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Restore from a local backup file (ZIP or JSON).
     */
    suspend fun restoreFromSpecificFile(db: AppDatabase, targetFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val restoreResult = RestoreEngine.restoreFromFile(
                context = context,
                db = db,
                backupFile = targetFile
            )
            if (restoreResult.success) {
                Result.success("تمت استعادة البيانات بنجاح من الملف (${targetFile.name})!\n${restoreResult.message}")
            } else {
                Result.failure(IOException(restoreResult.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreLatestBackup(db: AppDatabase): Result<String> = withContext(Dispatchers.IO) {
        val files = getAvailableBackupFiles()
        val newest = files.maxByOrNull { it.timestamp }
            ?: return@withContext Result.failure(IOException("لا توجد نسخ احتياطية متوفرة للاستعادة"))

        restoreFromSpecificFile(db, newest.file)
    }

    fun checkForAvailableBackup(): Boolean {
        val lastTime = prefs.getLong(KEY_LAST_BACKUP_TIME, 0L)
        if (lastTime > 0L) return true
        val backupDir = BackupEngine.getBackupDirectory(context)
        val files = backupDir.listFiles { f -> f.extension.equals("zip", true) || f.extension.equals("json", true) }
        return !files.isNullOrEmpty()
    }

    fun getLastBackupTimeFormatted(): String {
        val lastTime = prefs.getLong(KEY_LAST_BACKUP_TIME, 0L)
        if (lastTime > 0L) {
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            return sdf.format(Date(lastTime))
        }

        val backupDir = BackupEngine.getBackupDirectory(context)
        val files = backupDir.listFiles { f -> f.extension.equals("zip", true) || f.extension.equals("json", true) }
        val newest = files?.maxByOrNull { it.lastModified() }
        if (newest != null) {
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            return sdf.format(Date(newest.lastModified()))
        }

        return "لا توجد نسخة حتى الآن"
    }

    fun getAvailableBackupFiles(): List<BackupFileInfo> {
        val backupDir = BackupEngine.getBackupDirectory(context)
        val files = backupDir.listFiles { f -> f.extension.equals("zip", true) || f.extension.equals("json", true) }
            ?: return emptyList()

        val list = mutableListOf<BackupFileInfo>()
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

        for (file in files) {
            val sizeFormatted = "${file.length() / 1024} KB"
            list.add(
                BackupFileInfo(
                    fileName = file.name,
                    file = file,
                    backupDate = sdf.format(Date(file.lastModified())),
                    appVersion = "3.0",
                    dbVersion = 12,
                    fileSizeFormatted = sizeFormatted,
                    clientCount = 0,
                    invoiceCount = 0,
                    paymentCount = 0,
                    installmentCount = 0,
                    timestamp = file.lastModified(),
                    isDriveCloud = false
                )
            )
        }

        return list.sortedByDescending { it.timestamp }
    }
}
