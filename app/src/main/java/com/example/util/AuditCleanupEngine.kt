package com.example.util

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.data.database.AppDatabase
import com.example.data.model.AuditCleanupPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object AuditCleanupEngine {

    const val AVERAGE_AUDIT_ROW_BYTES = 220L // Conservative average byte estimate per AuditLog record

    /**
     * Calculates database file size on disk including WAL.
     */
    fun getDatabaseFileSizeBytes(context: Context): Long {
        return try {
            val dbFile = context.getDatabasePath("client_accounts_pro_db")
            val walFile = File(dbFile.parentFile, "client_accounts_pro_db-wal")
            var size = 0L
            if (dbFile.exists()) size += dbFile.length()
            if (walFile.exists()) size += walFile.length()
            size
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Estimates active audit log size in bytes.
     */
    fun estimateAuditLogsSizeBytes(recordCount: Int): Long {
        return recordCount.toLong() * AVERAGE_AUDIT_ROW_BYTES
    }

    /**
     * Formats bytes to human-readable Arabic string.
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f جيجابايت", bytes.toDouble() / (1024 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f ميجابايت", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%.1f كيلوبايت", bytes.toDouble() / 1024)
            else -> "$bytes بايت"
        }
    }

    /**
     * Builds a detailed preview of records to be deleted.
     */
    suspend fun previewDateRange(
        db: AppDatabase,
        startTime: Long,
        endTime: Long,
        title: String,
        safetyLockDays: Int
    ): AuditCleanupPreview = withContext(Dispatchers.IO) {
        val auditLogDao = db.auditLogDao()
        val count = auditLogDao.getCountBetween(startTime, endTime)
        val estimatedSize = estimateAuditLogsSizeBytes(count)

        val dateFmt = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val periodDesc = "${dateFmt.format(Date(startTime))} ← ${dateFmt.format(Date(endTime))}"

        val now = System.currentTimeMillis()
        val safetyLockCutoff = now - (safetyLockDays * 86400000L)
        val isProtected = endTime > safetyLockCutoff
        val warning = if (isProtected) {
            "تحذير: الفترة المختارة تتضمن سجلات حديثة (أقل من $safetyLockDays يومًا) مشمولة بقفل حماية السجلات الحديثة."
        } else null

        AuditCleanupPreview(
            operationTitle = title,
            affectedRecordCount = count,
            periodDescription = periodDesc,
            startTimestamp = startTime,
            endTimestamp = endTime,
            estimatedSizeBytes = estimatedSize,
            isProtectedBySafetyLock = isProtected,
            safetyLockWarning = warning
        )
    }

    /**
     * Builds a detailed preview of records older than cutoff time.
     */
    suspend fun previewOlderThan(
        db: AppDatabase,
        cutoffTime: Long,
        title: String,
        safetyLockDays: Int
    ): AuditCleanupPreview = withContext(Dispatchers.IO) {
        val auditLogDao = db.auditLogDao()
        val count = auditLogDao.getCountOlderThan(cutoffTime)
        val estimatedSize = estimateAuditLogsSizeBytes(count)

        val dateFmt = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val periodDesc = "السجلات الأقدم من ${dateFmt.format(Date(cutoffTime))}"

        val now = System.currentTimeMillis()
        val safetyLockCutoff = now - (safetyLockDays * 86400000L)
        val isProtected = cutoffTime > safetyLockCutoff
        val warning = if (isProtected) {
            "تحذير: تاريخ القطع يتضمن سجلات حديثة (أقل من $safetyLockDays يومًا)."
        } else null

        AuditCleanupPreview(
            operationTitle = title,
            affectedRecordCount = count,
            periodDescription = periodDesc,
            startTimestamp = 0L,
            endTimestamp = cutoffTime,
            estimatedSizeBytes = estimatedSize,
            isProtectedBySafetyLock = isProtected,
            safetyLockWarning = warning
        )
    }

    /**
     * Executes safe, non-blocking batch deletion for a date range.
     * Batch size defaults to 500 rows per transaction.
     */
    suspend fun executeBatchDeleteRange(
        context: Context,
        db: AppDatabase,
        startTime: Long,
        endTime: Long,
        batchSize: Int = 500,
        onProgress: suspend (deletedSoFar: Int, totalToDelete: Int, percent: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val auditLogDao = db.auditLogDao()
        val totalToDelete = auditLogDao.getCountBetween(startTime, endTime)
        if (totalToDelete == 0) return@withContext 0

        var deletedCount = 0
        while (deletedCount < totalToDelete) {
            val deletedInBatch = auditLogDao.deleteBatchBetween(startTime, endTime, batchSize)
            if (deletedInBatch <= 0) break
            deletedCount += deletedInBatch
            val percent = ((deletedCount.toDouble() / totalToDelete) * 100).toInt().coerceIn(0, 100)
            onProgress(deletedCount, totalToDelete, percent)
        }

        // Post-cleanup SQLite WAL maintenance
        if (deletedCount > 1000) {
            optimizeDatabaseStorage(db)
        }

        deletedCount
    }

    /**
     * Executes safe, non-blocking batch deletion for records older than cutoff.
     */
    suspend fun executeBatchDeleteOlderThan(
        context: Context,
        db: AppDatabase,
        cutoffTime: Long,
        batchSize: Int = 500,
        onProgress: suspend (deletedSoFar: Int, totalToDelete: Int, percent: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val auditLogDao = db.auditLogDao()
        val totalToDelete = auditLogDao.getCountOlderThan(cutoffTime)
        if (totalToDelete == 0) return@withContext 0

        var deletedCount = 0
        while (deletedCount < totalToDelete) {
            val deletedInBatch = auditLogDao.deleteBatchOlderThan(cutoffTime, batchSize)
            if (deletedInBatch <= 0) break
            deletedCount += deletedInBatch
            val percent = ((deletedCount.toDouble() / totalToDelete) * 100).toInt().coerceIn(0, 100)
            onProgress(deletedCount, totalToDelete, percent)
        }

        // Post-cleanup SQLite WAL maintenance
        if (deletedCount > 1000) {
            optimizeDatabaseStorage(db)
        }

        deletedCount
    }

    /**
     * Executes SQLite WAL checkpoint to truncate journal and compact space safely.
     */
    suspend fun optimizeDatabaseStorage(db: AppDatabase) = withContext(Dispatchers.IO) {
        try {
            db.openHelper.writableDatabase.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)")).close()
        } catch (e: Exception) {
            // Non-fatal WAL checkpoint
        }
    }
}
