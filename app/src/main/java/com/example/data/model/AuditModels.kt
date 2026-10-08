package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

/**
 * Archived Audit Log entity stored in local SQLite database for fast querying,
 * searching, and pagination without unzipping external files.
 */
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "archived_audit_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["originalId"]),
        Index(value = ["tableName"]),
        Index(value = ["operationType"]),
        Index(value = ["archivePackageName"])
    ]
)
data class ArchivedAuditLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalId: Int,
    val timestamp: Long,
    val operationType: String,
    val tableName: String,
    val details: String,
    val archivePackageName: String = "",
    val archivedAt: Long = System.currentTimeMillis()
)

/**
 * Log record for every administrative action on audit logs (archive, delete, restore, export, cleanup).
 * Provides accountability, traceability, and auditability of the audit system itself.
 */
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "audit_management_operations",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["correlationId"]),
        Index(value = ["operationType"])
    ]
)
data class AuditManagementOperation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val operationType: String, // ARCHIVE, DELETE, RESTORE, EXPORT, CLEANUP
    val stage: String, // STARTED, COMPLETED, FAILED
    val correlationId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val userName: String = "المسؤول",
    val periodDescription: String,
    val recordCount: Int,
    val resultStatus: String, // ناجحة, فشلت, قيد المعالجة
    val durationMs: Long = 0L,
    val notes: String? = null,
    val detailsJson: String? = null
)

/**
 * Aggregated monthly statistics returned directly from SQLite database.
 */
data class MonthAuditSummary(
    val year: Int,
    val month: Int,
    val count: Int,
    val minTimestamp: Long,
    val maxTimestamp: Long
) {
    val monthKey: String get() = String.format("%04d-%02d", year, month)
    
    val monthNameArabic: String get() {
        return when (month) {
            1 -> "يناير"
            2 -> "فبراير"
            3 -> "مارس"
            4 -> "أبريل"
            5 -> "مايو"
            6 -> "يونيو"
            7 -> "يوليو"
            8 -> "أغسطس"
            9 -> "سبتمبر"
            10 -> "أكتوبر"
            11 -> "نوفمبر"
            12 -> "ديسمبر"
            else -> "شهر $month"
        }
    }

    val displayName: String get() = "$monthNameArabic $year"
}

/**
 * Complete statistics snapshot for the audit log management dashboard.
 */
data class AuditStatsSummary(
    val activeLogsCount: Int = 0,
    val archivedLogsCount: Int = 0,
    val oldestLogTimestamp: Long? = null,
    val newestLogTimestamp: Long? = null,
    val currentMonthCount: Int = 0,
    val oldLogsEligibleCount: Int = 0,
    val estimatedActiveSizeBytes: Long = 0L,
    val archiveDirectorySizeBytes: Long = 0L,
    val databaseFileSizeBytes: Long = 0L
)

/**
 * Operation preview before executing destructive cleanup or archive.
 */
data class AuditCleanupPreview(
    val operationTitle: String,
    val affectedRecordCount: Int,
    val periodDescription: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val estimatedSizeBytes: Long,
    val isProtectedBySafetyLock: Boolean = false,
    val safetyLockWarning: String? = null
)

/**
 * Manifest header embedded inside each structured ZIP archive package.
 */
@JsonClass(generateAdapter = true)
data class AuditArchiveManifest(
    val archiveVersion: Int = 1,
    val appVersion: String = "1.1",
    val databaseVersion: Int = 13,
    val packageName: String,
    val creationTimestamp: Long = System.currentTimeMillis(),
    val originalRecordCount: Int,
    val startDate: Long,
    val endDate: Long,
    val periodDescription: String,
    val sha256Checksum: String,
    val storeName: String,
    val createdBy: String
)

/**
 * Structured content of an audit archive package.
 */
@JsonClass(generateAdapter = true)
data class AuditArchivePackage(
    val manifest: AuditArchiveManifest,
    val logs: List<AuditLog>
)

/**
 * Result summary of an archive restore operation.
 */
data class AuditRestoreResult(
    val newlyRestoredCount: Int,
    val skippedDuplicatesCount: Int,
    val totalInArchive: Int,
    val archivePackageName: String
)

/**
 * Information about a physical archive file found in storage.
 */
data class ArchiveFileInfo(
    val fileName: String,
    val fileSizeBytes: Long,
    val lastModified: Long,
    val recordCount: Int,
    val periodDescription: String,
    val isValid: Boolean,
    val absolutePath: String
)
