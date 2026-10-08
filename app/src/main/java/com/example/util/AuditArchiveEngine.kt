package com.example.util

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object AuditArchiveEngine {

    fun getArchivesDirectory(context: Context): File {
        val dir = File(context.filesDir, "audit_archives")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    fun computeSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Creates a structured ZIP archive for a specified date range / month.
     * Guarantees process safety: uses .tmp file until validation succeeds.
     * Records are moved to archived_audit_logs and only removed from active table after verified.
     */
    suspend fun archiveRange(
        context: Context,
        db: AppDatabase,
        startTime: Long,
        endTime: Long,
        periodDescription: String,
        packageSlug: String,
        storeName: String,
        userName: String,
        correlationId: String,
        onProgress: suspend (progressPercent: Int, statusText: String) -> Unit = { _, _ -> }
    ): Result<ArchiveFileInfo> = withContext(Dispatchers.IO) {
        val archiveDir = getArchivesDirectory(context)
        val finalZipFile = File(archiveDir, "AuditLog_$packageSlug.zip")
        val tempZipFile = File(archiveDir, "AuditLog_${packageSlug}_${System.currentTimeMillis()}.tmp")

        try {
            onProgress(10, "🔍 جاري حصر وفحص سجلات الفترة ($periodDescription)...")
            val auditLogDao = db.auditLogDao()
            val archivedLogDao = db.archivedAuditLogDao()

            val logs = auditLogDao.getLogsBetween(startTime, endTime)
            if (logs.isEmpty()) {
                return@withContext Result.failure(Exception("لا توجد سجلات تطابق الفترة المحددة للأرشفة."))
            }

            onProgress(30, "📦 جاري تجهيز حزمة الأرشفة (${logs.size} سجل)...")

            val moshi = getMoshi()
            val listType = Types.newParameterizedType(List::class.java, AuditLog::class.java)
            val logsAdapter = moshi.adapter<List<AuditLog>>(listType).indent("  ")
            val manifestAdapter = moshi.adapter(AuditArchiveManifest::class.java).indent("  ")

            val logsJsonString = logsAdapter.toJson(logs)
            val logsBytes = logsJsonString.toByteArray(Charsets.UTF_8)
            val checksum = computeSha256(logsBytes)

            val manifest = AuditArchiveManifest(
                archiveVersion = 1,
                appVersion = "1.1",
                databaseVersion = 13,
                packageName = "AuditLog_$packageSlug.zip",
                creationTimestamp = System.currentTimeMillis(),
                originalRecordCount = logs.size,
                startDate = startTime,
                endDate = endTime,
                periodDescription = periodDescription,
                sha256Checksum = checksum,
                storeName = storeName,
                createdBy = userName
            )
            val manifestJsonString = manifestAdapter.toJson(manifest)

            val metadataMap = mapOf(
                "appName" to "نظام الحكيمي للحسابات والفواتير",
                "systemType" to "Audit Log Long-Term Archive",
                "creationDateFormatted" to SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date()),
                "totalRecords" to logs.size.toString(),
                "period" to periodDescription,
                "correlationId" to correlationId
            )
            val mapType = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
            val metadataJson = moshi.adapter<Map<String, String>>(mapType).indent("  ").toJson(metadataMap)

            onProgress(50, "💾 جاري كتابة ملف الأرشيف المؤقت المظغوط...")

            FileOutputStream(tempZipFile).use { fos ->
                ZipOutputStream(BufferedOutputStream(fos)).use { zos ->
                    // 1. manifest.json
                    zos.putNextEntry(ZipEntry("manifest.json"))
                    zos.write(manifestJsonString.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // 2. metadata.json
                    zos.putNextEntry(ZipEntry("metadata.json"))
                    zos.write(metadataJson.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // 3. audit_logs.json
                    zos.putNextEntry(ZipEntry("audit_logs.json"))
                    zos.write(logsBytes)
                    zos.closeEntry()
                }
            }

            onProgress(70, "🛡️ جاري التحقق الصارم من سلامة حزمة الأرشيف...")

            // Validation step: read back and verify checksum and count
            val validationResult = validateArchiveFile(tempZipFile)
            if (!validationResult.isValid || validationResult.recordCount != logs.size) {
                tempZipFile.delete()
                return@withContext Result.failure(Exception("فشل التحقق من تكامل حزمة الأرشيف! لم يتم حذف أي سجل من قاعدة البيانات."))
            }

            // Atomic rename to final zip
            if (finalZipFile.exists()) {
                finalZipFile.delete()
            }
            if (!tempZipFile.renameTo(finalZipFile)) {
                // Fallback copy if rename fails
                tempZipFile.copyTo(finalZipFile, overwrite = true)
                tempZipFile.delete()
            }

            onProgress(85, "🗃️ جاري ترحيل السجلات إلى جدول الأرشيف بقاعدة البيانات...")

            // Move records to archived_audit_logs in batches of 500
            val archivedEntities = logs.map { log ->
                ArchivedAuditLog(
                    originalId = log.id,
                    timestamp = log.timestamp,
                    operationType = log.operationType,
                    tableName = log.tableName,
                    details = log.details,
                    archivePackageName = finalZipFile.name,
                    archivedAt = System.currentTimeMillis()
                )
            }
            archivedEntities.chunked(500).forEach { chunk ->
                archivedLogDao.insertArchivedLogs(chunk)
            }

            onProgress(95, "🧹 جاري تفريغ السجلات المؤرشفة بأمان من السجل النشط...")

            // Safe batch deletion from active table
            val batchSize = 500
            var totalDeleted = 0
            while (totalDeleted < logs.size) {
                val deletedInBatch = auditLogDao.deleteBatchBetween(startTime, endTime, batchSize)
                if (deletedInBatch <= 0) break
                totalDeleted += deletedInBatch
            }

            onProgress(100, "✅ اكتملت الأرشفة بنجاح (${logs.size} سجل)!")

            Result.success(
                ArchiveFileInfo(
                    fileName = finalZipFile.name,
                    fileSizeBytes = finalZipFile.length(),
                    lastModified = finalZipFile.lastModified(),
                    recordCount = logs.size,
                    periodDescription = periodDescription,
                    isValid = true,
                    absolutePath = finalZipFile.absolutePath
                )
            )
        } catch (e: Exception) {
            if (tempZipFile.exists()) {
                tempZipFile.delete()
            }
            Result.failure(e)
        }
    }

    /**
     * Validates a physical ZIP archive file without modifying database.
     */
    fun validateArchiveFile(file: File): ArchiveFileInfo {
        if (!file.exists() || file.length() <= 0) {
            return ArchiveFileInfo(file.name, 0, 0, 0, "ملف غير صالح", false, file.absolutePath)
        }

        return try {
            ZipFile(file).use { zip ->
                val manifestEntry = zip.getEntry("manifest.json")
                val logsEntry = zip.getEntry("audit_logs.json")

                if (manifestEntry == null || logsEntry == null) {
                    return ArchiveFileInfo(file.name, file.length(), file.lastModified(), 0, "مفقود ملفات الحزمة", false, file.absolutePath)
                }

                val moshi = getMoshi()
                val manifestJson = zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val manifest = moshi.adapter(AuditArchiveManifest::class.java).fromJson(manifestJson)
                    ?: return ArchiveFileInfo(file.name, file.length(), file.lastModified(), 0, "بيانات الحزمة تالفة", false, file.absolutePath)

                // Verify checksum
                val logsBytes = zip.getInputStream(logsEntry).use { it.readBytes() }
                val computedChecksum = computeSha256(logsBytes)
                val isChecksumValid = computedChecksum.equals(manifest.sha256Checksum, ignoreCase = true)

                ArchiveFileInfo(
                    fileName = file.name,
                    fileSizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    recordCount = manifest.originalRecordCount,
                    periodDescription = manifest.periodDescription,
                    isValid = isChecksumValid,
                    absolutePath = file.absolutePath
                )
            }
        } catch (e: Exception) {
            ArchiveFileInfo(file.name, file.length(), file.lastModified(), 0, "خطأ في قراءة الأرشيف: ${e.message}", false, file.absolutePath)
        }
    }

    /**
     * Restores archived logs into the active table with DUPLICATE PROTECTION.
     */
    suspend fun restoreArchive(
        context: Context,
        db: AppDatabase,
        archiveFile: File,
        onProgress: suspend (progressPercent: Int, statusText: String) -> Unit = { _, _ -> }
    ): Result<AuditRestoreResult> = withContext(Dispatchers.IO) {
        try {
            onProgress(10, "🔍 جاري فحص ملف الأرشيف (${archiveFile.name})...")

            val validation = validateArchiveFile(archiveFile)
            if (!validation.isValid) {
                return@withContext Result.failure(Exception("ملف الأرشيف غير صالح أو تالف! تم إلغاء الاستعادة."))
            }

            onProgress(30, "📖 جاري فك واستخراج السجلات من الحزمة...")

            val moshi = getMoshi()
            val listType = Types.newParameterizedType(List::class.java, AuditLog::class.java)
            val logsAdapter = moshi.adapter<List<AuditLog>>(listType)

            val archivedLogs: List<AuditLog> = ZipFile(archiveFile).use { zip ->
                val logsEntry = zip.getEntry("audit_logs.json")
                    ?: return@withContext Result.failure(Exception("ملف سجلات العمليات مفقود من الأرشيف."))
                val jsonText = zip.getInputStream(logsEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                logsAdapter.fromJson(jsonText) ?: emptyList()
            }

            if (archivedLogs.isEmpty()) {
                return@withContext Result.success(AuditRestoreResult(0, 0, 0, archiveFile.name))
            }

            onProgress(50, "🛡️ جاري كشف السجلات المكررة لمنع الازدواجية...")

            val auditLogDao = db.auditLogDao()
            val minTs = archivedLogs.minOf { it.timestamp }
            val maxTs = archivedLogs.maxOf { it.timestamp }

            // Existing active logs in this timestamp span
            val existingActiveLogs = auditLogDao.getLogsBetween(minTs, maxTs)
            val existingKeys = existingActiveLogs.map { "${it.timestamp}_${it.tableName}_${it.operationType}_${it.details}" }.toSet()

            val toInsert = mutableListOf<AuditLog>()
            var duplicatesCount = 0

            archivedLogs.forEach { log ->
                val key = "${log.timestamp}_${log.tableName}_${log.operationType}_${log.details}"
                if (existingKeys.contains(key)) {
                    duplicatesCount++
                } else {
                    // Strip old id so autogenerate creates fresh primary keys safely
                    toInsert.add(
                        AuditLog(
                            id = 0,
                            timestamp = log.timestamp,
                            operationType = log.operationType,
                            tableName = log.tableName,
                            details = log.details
                        )
                    )
                }
            }

            onProgress(80, "💾 جاري استعادة السجلات الجديدة (${toInsert.size} سجل جديد، $duplicatesCount تم تجاهلها)...")

            if (toInsert.isNotEmpty()) {
                toInsert.chunked(500).forEach { batch ->
                    auditLogDao.insertAll(batch)
                }
            }

            onProgress(100, "✅ اكتملت الاستعادة بنجاح!")

            Result.success(
                AuditRestoreResult(
                    newlyRestoredCount = toInsert.size,
                    skippedDuplicatesCount = duplicatesCount,
                    totalInArchive = archivedLogs.size,
                    archivePackageName = archiveFile.name
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lists all existing valid and pending archive files in storage.
     */
    fun listArchiveFiles(context: Context): List<ArchiveFileInfo> {
        val dir = getArchivesDirectory(context)
        val files = dir.listFiles { f -> f.extension.equals("zip", ignoreCase = true) } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.map { file ->
            validateArchiveFile(file)
        }
    }

    /**
     * Deletes a specific archive file from device storage.
     */
    fun deleteArchiveFile(file: File): Boolean {
        return if (file.exists()) file.delete() else false
    }

    /**
     * Calculates total disk space consumed by audit archives.
     */
    fun getArchivesTotalSizeBytes(context: Context): Long {
        val dir = getArchivesDirectory(context)
        return dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }
}
