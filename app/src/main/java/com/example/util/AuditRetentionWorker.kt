package com.example.util

import android.content.Context
import androidx.work.*
import com.example.data.database.AppDatabase
import com.example.data.model.AuditManagementOperation
import com.example.data.preferences.AuditRetentionPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

class AuditRetentionWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val preferences = AuditRetentionPreferences(appContext)
            val settings = preferences.retentionSettingsFlow.first()
            val cutoff = settings.cutoffTimestampMillis

            if (cutoff == null || settings.retentionPeriodKey == "FOREVER") {
                return@withContext Result.success()
            }

            val db = AppDatabase.getDatabase(appContext)
            val auditLogDao = db.auditLogDao()
            val eligibleCount = auditLogDao.getCountOlderThan(cutoff)

            if (eligibleCount <= 0) {
                return@withContext Result.success()
            }

            val correlationId = UUID.randomUUID().toString()
            val opDao = db.auditOperationDao()
            val opRecord = AuditManagementOperation(
                operationType = "BACKGROUND_CLEANUP",
                stage = "STARTED",
                correlationId = correlationId,
                periodDescription = "تطبيق سياسة الاحتفاظ (${settings.retentionPeriodLabelArabic})",
                recordCount = eligibleCount,
                resultStatus = "قيد المعالجة"
            )
            val opId = opDao.insertOperation(opRecord)
            val startTime = System.currentTimeMillis()

            val deletedCount = AuditCleanupEngine.executeBatchDeleteOlderThan(
                context = appContext,
                db = db,
                cutoffTime = cutoff,
                batchSize = 500,
                onProgress = { _, _, _ -> }
            )

            val durationMs = System.currentTimeMillis() - startTime
            opDao.updateOperation(
                opRecord.copy(
                    id = opId.toInt(),
                    stage = "COMPLETED",
                    recordCount = deletedCount,
                    resultStatus = "ناجحة",
                    durationMs = durationMs
                )
            )
            preferences.recordCleanupTimestamp()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "AuditRetentionPeriodicCleanup"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresDeviceIdle(false)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<AuditRetentionWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
