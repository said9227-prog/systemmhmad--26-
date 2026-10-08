package com.example.util

import android.content.Context
import android.net.Uri
import com.example.data.database.AppDatabase
import com.example.data.model.BackupHistory
import com.example.data.model.StoreSettings
import com.example.data.preferences.BackupPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupExecutionResult(
    val success: Boolean,
    val message: String,
    val fileName: String? = null,
    val fileSize: Long = 0L,
    val backupType: String = "FULL_BACKUP"
)

object BackupExecutionCoordinator {

    private val backupMutex = Mutex()

    /**
     * Executes backup (Full Backup, CSV, or PDF) to the configured SAF destination folder.
     * Guaranteed thread-safe with Mutex preventing overlapping executions.
     */
    suspend fun executeBackup(
        context: Context,
        db: AppDatabase,
        overrideType: String? = null,
        isTriggeredByWorkManager: Boolean = false,
        onProgress: suspend (progress: Int, stage: String) -> Unit = { _, _ -> }
    ): BackupExecutionResult = withContext(Dispatchers.IO) {
        if (!backupMutex.tryLock()) {
            return@withContext BackupExecutionResult(
                success = false,
                message = "عملية نسخ احتياطي أخرى قيد التنفيذ حالياً، يرجى الانتظار لحين اكتمالها"
            )
        }

        try {
            val backupPreferences = BackupPreferences(context)
            val settingsState = backupPreferences.backupSettingsFlow.first()

            val effectiveType = overrideType ?: settingsState.backupType
            val folderUriStr = settingsState.backupFolderUri

            if (folderUriStr.isNullOrBlank()) {
                val errorMsg = "يرجى تحديد مجلد النسخ الاحتياطي أولاً"
                backupPreferences.updateSyncStatus("Folder Required", errorMsg)
                if (isTriggeredByWorkManager) {
                    BackupNotificationManager.showError(
                        context,
                        "فشل النسخ الاحتياطي التلقائي",
                        "لم يتم تحديد مجلد الحفظ. يرجى فتح التطبيق وتحديد المجلد."
                    )
                }
                return@withContext BackupExecutionResult(success = false, message = errorMsg)
            }

            val treeUri = Uri.parse(folderUriStr)

            // Validate SAF Permissions
            if (!SafStorageManager.hasPersistablePermission(context, treeUri)) {
                val errorMsg = "تم فقدان تصريح الوصول للمجلد. يرجى إعادة اختياره لمنح الإذن."
                backupPreferences.updateSyncStatus("Permission Lost", errorMsg)
                if (isTriggeredByWorkManager) {
                    BackupNotificationManager.showError(
                        context,
                        "فقدان إذن المجلد",
                        "تعذر الوصول للمجلد المحدد. اضغط لإعادة منحه الإذن."
                    )
                }
                return@withContext BackupExecutionResult(success = false, message = errorMsg)
            }

            // Validate Folder Accessibility
            if (!SafStorageManager.isFolderAccessible(context, treeUri)) {
                val errorMsg = "تعذر الوصول للمجلد المحدد أو أنه لم يعد موجوداً."
                backupPreferences.updateSyncStatus("Folder Unavailable", errorMsg)
                if (isTriggeredByWorkManager) {
                    BackupNotificationManager.showError(
                        context,
                        "المجلد غير متاح",
                        "يرجى التحقق من توفر مجلد النسخ الاحتياطي أو اختيار مجلد جديد."
                    )
                }
                return@withContext BackupExecutionResult(success = false, message = errorMsg)
            }

            // Begin backup execution
            backupPreferences.recordBackupAttempt()
            onProgress(15, "⚡ جاري تجهيز البيانات وحساب التشفير...")

            val now = System.currentTimeMillis()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
            val storeSettings = db.storeSettingsDao().getSettings() ?: StoreSettings()

            var tempSourceFile: File? = null
            var mimeType = "application/zip"
            var targetFileName = "backup_$timeStamp.zip"

            when (effectiveType.uppercase(Locale.US)) {
                "CSV" -> {
                    onProgress(35, "📊 جاري استخراج وتنسيق ملفات CSV مع ترميز UTF-8 BOM...")
                    mimeType = "text/csv"
                    targetFileName = "export_$timeStamp.csv"
                    tempSourceFile = ExportEngine.exportToCsv(
                        context = context,
                        db = db,
                        selectedCategories = BackupEngine.ALL_CATEGORIES.toSet(),
                        settings = storeSettings
                    )
                }
                "PDF" -> {
                    onProgress(35, "📄 جاري إنشاء التقرير المالي الشامل بتنسيق PDF...")
                    mimeType = "application/pdf"
                    targetFileName = "report_$timeStamp.pdf"
                    tempSourceFile = ExportEngine.exportToPdf(
                        context = context,
                        db = db,
                        selectedCategories = BackupEngine.ALL_CATEGORIES.toSet(),
                        settings = storeSettings
                    )
                }
                else -> {
                    // Full Backup (Official restore format: structured ZIP)
                    onProgress(35, "📦 جاري ضغط ملفات JSON وإنشاء النسخة المتكاملة...")
                    mimeType = "application/zip"
                    targetFileName = "backup_$timeStamp.zip"
                    val backupResult = BackupEngine.createBackup(
                        context = context,
                        db = db,
                        destination = "مجلد خارجي",
                        externalFolderUri = null, // We write to SAF directly below
                        onProgress = onProgress
                    )
                    tempSourceFile = backupResult.getOrNull()
                }
            }

            if (tempSourceFile == null || !tempSourceFile.exists() || tempSourceFile.length() <= 0L) {
                val errorMsg = "فشل توليد ملف النسخة الاحتياطية أو الملف فارغ"
                backupPreferences.recordBackupFailure(errorMsg)
                if (isTriggeredByWorkManager) {
                    BackupNotificationManager.showError(context, "فشل إنشاء النسخة", errorMsg)
                }
                return@withContext BackupExecutionResult(success = false, message = errorMsg)
            }

            onProgress(75, "📤 جاري النقل والحفظ بأمان في مجلد التخزين (SAF)...")

            // Write to SAF directory
            val writeResult = SafStorageManager.writeToSafFolder(
                context = context,
                treeUri = treeUri,
                sourceFile = tempSourceFile,
                mimeType = mimeType,
                targetFileName = targetFileName
            )

            val createdDoc = writeResult.getOrNull()
            if (createdDoc == null) {
                val errorMsg = writeResult.exceptionOrNull()?.localizedMessage ?: "فشلت كتابة الملف في المجلد المحدد"
                backupPreferences.recordBackupFailure(errorMsg)
                if (isTriggeredByWorkManager) {
                    BackupNotificationManager.showError(context, "فشل الحفظ في المجلد", errorMsg)
                }
                return@withContext BackupExecutionResult(success = false, message = errorMsg)
            }

            val finalFileSize = createdDoc.length()

            // Enforce retention policy: delete oldest backups only AFTER new backup has succeeded
            onProgress(90, "🧹 جاري فحص سياسة الاحتفاظ بالنسخ (الاحتفاظ بآخر ${settingsState.retentionCount} نسخ)...")
            SafStorageManager.enforceRetention(context, treeUri, settingsState.retentionCount)

            // Update persistent preferences and history
            backupPreferences.recordBackupSuccess(
                type = effectiveType,
                fileName = targetFileName,
                fileSizeBytes = finalFileSize
            )

            val destTitle = SafStorageManager.getFolderDisplayName(context, treeUri)
            val totalRecords = try {
                db.clientDao().getAllClients().size + db.invoiceDao().getAllInvoices().size + db.paymentDao().getAllPayments().size
            } catch (e: Exception) {
                0
            }

            db.backupHistoryDao().insert(
                BackupHistory(
                    timestamp = now,
                    destination = destTitle,
                    backupType = if (isTriggeredByWorkManager) "تلقائي ($effectiveType)" else "يدوي ($effectiveType)",
                    status = "ناجحة",
                    fileSizeBytes = finalFileSize,
                    recordCount = totalRecords
                )
            )

            // Check if Google Drive Sync is enabled
            val drivePrefs = context.getSharedPreferences("drive_sync_prefs", Context.MODE_PRIVATE)
            if (drivePrefs.getBoolean("is_sync_enabled", false)) {
                try {
                    onProgress(95, "☁️ جاري المزامنة التلقائية مع Google Drive...")
                    val driveManager = GoogleDriveBackupManager(context)
                    driveManager.createAndUploadBackup(db)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            onProgress(100, "✓ اكتملت العملية وحُفظ الملف بنجاح تام!")

            // Clean up temp file
            try {
                if (tempSourceFile.exists()) tempSourceFile.delete()
            } catch (_: Exception) {}

            BackupExecutionResult(
                success = true,
                message = "تم حفظ ملف النسخة ($targetFileName) بنجاح في: $destTitle",
                fileName = targetFileName,
                fileSize = finalFileSize,
                backupType = effectiveType
            )
        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = e.localizedMessage ?: "حدث خطأ غير متوقع أثناء النسخ"
            val backupPreferences = BackupPreferences(context)
            backupPreferences.recordBackupFailure(errorMsg)
            if (isTriggeredByWorkManager) {
                BackupNotificationManager.showError(context, "فشل النسخ التلقائي", errorMsg)
            }
            BackupExecutionResult(success = false, message = errorMsg)
        } finally {
            backupMutex.unlock()
        }
    }
}
