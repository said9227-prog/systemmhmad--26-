package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object SafStorageManager {

    /**
     * Persists read and write permissions for the selected SAF Tree URI.
     */
    fun takePersistablePermissions(context: Context, treeUri: Uri): Boolean {
        return try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Checks if the app currently holds persistable read and write permissions for the given Tree URI.
     */
    fun hasPersistablePermission(context: Context, treeUri: Uri): Boolean {
        return try {
            val persisted = context.contentResolver.persistedUriPermissions
            persisted.any { it.uri == treeUri && it.isWritePermission && it.isReadPermission }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the folder exists, is a directory, and is writable.
     */
    fun isFolderAccessible(context: Context, treeUri: Uri): Boolean {
        return try {
            val documentDir = DocumentFile.fromTreeUri(context, treeUri)
            documentDir != null && documentDir.exists() && documentDir.isDirectory && documentDir.canWrite()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Resolves a user-friendly display name for the selected SAF folder.
     * Accurately labels Google Drive folders when selected by the user.
     */
    fun getFolderDisplayName(context: Context, treeUri: Uri): String {
        return try {
            val documentDir = DocumentFile.fromTreeUri(context, treeUri)
            val name = documentDir?.name
            val uriStr = treeUri.toString().lowercase()

            val isGoogleDrive = uriStr.contains("com.google.android.apps.docs.storage") ||
                    uriStr.contains("googledrive") ||
                    uriStr.contains("drive")

            when {
                !name.isNullOrBlank() && isGoogleDrive -> "Google Drive / $name"
                !name.isNullOrBlank() -> name
                isGoogleDrive -> "Google Drive / النسخ الاحتياطي"
                else -> "مجلد مخصص (${treeUri.lastPathSegment ?: "SAF"})"
            }
        } catch (e: Exception) {
            "مجلد التخزين المحدد"
        }
    }

    /**
     * Writes a local file to the selected SAF folder via ContentResolver.
     * Uses streaming to avoid keeping large data in memory.
     * Verifies that the created file exists and has size > 0.
     */
    suspend fun writeToSafFolder(
        context: Context,
        treeUri: Uri,
        sourceFile: File,
        mimeType: String,
        targetFileName: String
    ): Result<DocumentFile> = withContext(Dispatchers.IO) {
        try {
            val documentDir = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.failure(IllegalStateException("تعذر الوصول إلى مجلد الحفظ المحدد"))

            if (!documentDir.exists() || !documentDir.isDirectory || !documentDir.canWrite()) {
                return@withContext Result.failure(IllegalStateException("المجلد المحدد غير متاح أو لا يملك إذن كتابة"))
            }

            // Create new file inside SAF directory
            val newDocFile = documentDir.createFile(mimeType, targetFileName)
                ?: return@withContext Result.failure(IOException("فشل إنشاء الملف في مجلد التخزين المحدد"))

            // Stream data from source file to ContentResolver OutputStream
            context.contentResolver.openOutputStream(newDocFile.uri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                    outputStream.flush()
                }
            } ?: return@withContext Result.failure(IOException("تعذر فتح تيار الإخراج للكتابة في المجلد"))

            // Verify file size and existence
            if (newDocFile.length() <= 0L) {
                newDocFile.delete()
                return@withContext Result.failure(IOException("فشل التحقق: حجم الملف المنشأ في المجلد هو 0 بايت"))
            }

            Result.success(newDocFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Enforces the retention policy in the SAF directory.
     * Only deletes old backups AFTER a new backup has successfully completed.
     * Never deletes all backups.
     */
    suspend fun enforceRetention(
        context: Context,
        treeUri: Uri,
        retentionCount: Int
    ) = withContext(Dispatchers.IO) {
        try {
            if (retentionCount <= 0) return@withContext

            val documentDir = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext
            if (!documentDir.exists() || !documentDir.isDirectory) return@withContext

            // Find all backup files created by the application
            val backupFiles = documentDir.listFiles().filter { doc ->
                val name = doc.name ?: ""
                (name.startsWith("backup_") || name.startsWith("تقرير_")) &&
                        (name.endsWith(".zip") || name.endsWith(".csv") || name.endsWith(".pdf") || name.endsWith(".txt"))
            }.sortedBy { it.lastModified() }

            val deleteTargetCount = backupFiles.size - retentionCount
            if (deleteTargetCount > 0) {
                // Delete oldest files beyond retention limit
                for (i in 0 until deleteTargetCount) {
                    try {
                        backupFiles[i].delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Copies a file from a SAF document URI to a local temporary cache file for inspection/restoration.
     */
    suspend fun copyUriToTempFile(
        context: Context,
        documentUri: Uri,
        tempFileName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(context.cacheDir, tempFileName)
            if (tempFile.exists()) {
                tempFile.delete()
            }

            context.contentResolver.openInputStream(documentUri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                    outputStream.flush()
                }
            } ?: return@withContext Result.failure(IOException("تعذر فتح ملف النسخة الاحتياطية للقراءة"))

            if (!tempFile.exists() || tempFile.length() <= 0L) {
                return@withContext Result.failure(IOException("الملف المستلم فارغ أو تعذر نسخه مؤقتاً"))
            }

            Result.success(tempFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
