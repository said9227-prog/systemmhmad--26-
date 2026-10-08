package com.example.util

import android.content.Context
import androidx.work.*
import com.example.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class GoogleDriveSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME_INSTANT = "GoogleDriveInstantSync"
        const val WORK_NAME_PERIODIC = "GoogleDrivePeriodicSync"
        const val KEY_IS_MANUAL = "is_manual"
        const val KEY_IS_CATCH_UP = "is_catch_up"

        /**
         * Enqueues an instant one-time background sync when data modifications occur.
         * Guarantees network connectivity constraint and exponential retry backoff.
         */
        fun enqueueInstantSync(context: Context, isManual: Boolean = false, isCatchUp: Boolean = false) {
            val driveManager = GoogleDriveBackupManager(context)
            if (!driveManager.isSyncEnabled()) return

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val data = workDataOf(
                KEY_IS_MANUAL to isManual,
                KEY_IS_CATCH_UP to isCatchUp
            )

            val syncRequest = OneTimeWorkRequestBuilder<GoogleDriveSyncWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_INSTANT,
                if (isManual) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                syncRequest
            )
        }

        /**
         * Enqueues a catch-up sync task that triggers the instant network becomes available.
         */
        fun enqueueCatchUpSync(context: Context) {
            val driveManager = GoogleDriveBackupManager(context)
            if (!driveManager.isSyncEnabled()) return

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val data = workDataOf(
                KEY_IS_MANUAL to false,
                KEY_IS_CATCH_UP to true
            )

            val syncRequest = OneTimeWorkRequestBuilder<GoogleDriveSyncWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                GoogleDriveSyncScheduler.CATCH_UP_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                syncRequest
            )
        }

        /**
         * Enqueues periodic background sync with Google Drive.
         */
        fun enqueuePeriodicSync(context: Context, intervalDays: Long = 1L) {
            val driveManager = GoogleDriveBackupManager(context)
            if (!driveManager.isSyncEnabled()) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<GoogleDriveSyncWorker>(
                intervalDays, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
        }

        fun cancelSync(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(WORK_NAME_INSTANT)
            wm.cancelUniqueWork(WORK_NAME_PERIODIC)
            wm.cancelUniqueWork(GoogleDriveSyncScheduler.CATCH_UP_WORK_NAME)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val driveManager = GoogleDriveBackupManager(context)
            if (!driveManager.isSyncEnabled()) {
                return@withContext Result.success()
            }

            val accountEmail = driveManager.getSavedAccountEmail()
            if (accountEmail.isNullOrBlank()) {
                cancelSync(context)
                return@withContext Result.success()
            }

            val isManual = inputData.getBoolean(KEY_IS_MANUAL, false)
            val freq = GoogleDriveSyncScheduler.getFrequency(context)
            val currentPeriodKey = GoogleDriveSyncScheduler.getCurrentPeriodKey(freq)
            val lastSuccessKey = GoogleDriveSyncScheduler.getLastSuccessPeriodKey(context)

            // Strict anti-duplication rule: If not manual, never run twice within the same period!
            if (!isManual && lastSuccessKey == currentPeriodKey) {
                return@withContext Result.success()
            }

            BackupNotificationManager.showProgress(
                context,
                "جاري مزامنة ورفع البيانات إلى Google Drive...",
                30
            )

            val db = AppDatabase.getDatabase(context)
            val syncResult = driveManager.createAndUploadBackup(
                db = db,
                accountEmail = accountEmail,
                onProgress = { percent, stage ->
                    BackupNotificationManager.showProgress(context, stage, percent)
                }
            )

            if (syncResult.isSuccess) {
                GoogleDriveSyncScheduler.recordSuccessForCurrentPeriod(context)
                BackupNotificationManager.showSuccess(
                    context,
                    "تمت المزامنة مع Google Drive بنجاح ✅",
                    syncResult.getOrDefault("تم حفظ وتحديث النسخة الاحتياطية في مجلد الحكيمي للأدوية والمستلزمات الطبية.")
                )
                Result.success()
            } else {
                val error = syncResult.exceptionOrNull()
                val isRecoverableAuth = error is com.google.android.gms.auth.UserRecoverableAuthException
                
                if (isRecoverableAuth) {
                    driveManager.setSyncEnabled(false, null)
                    cancelSync(context)
                    BackupNotificationManager.showError(
                        context,
                        "مطلوب تأكيد الإذن من Google",
                        "يرجى فتح التطبيق والانتقال لقسم النسخ الاحتياطي لمنح الإذن."
                    )
                    Result.success()
                } else {
                    val isNetworkError = error?.message?.contains("NO_INTERNET", ignoreCase = true) == true ||
                            error?.message?.contains("timeout", ignoreCase = true) == true ||
                            error?.message?.contains("connection", ignoreCase = true) == true

                    if (isNetworkError && runAttemptCount < 2) {
                        Result.retry()
                    } else {
                        BackupNotificationManager.showError(
                            context,
                            "فشلت المزامنة مع Google Drive ❌",
                            error?.localizedMessage ?: "حدث خطأ أثناء مزامنة البيانات السحابية."
                        )
                        Result.success()
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            BackupNotificationManager.showError(
                context,
                "توقف المزامنة السحابية ⚠️",
                e.localizedMessage ?: "حدث خطأ أثناء الاتصال بالحساب السحابي."
            )
            Result.success()
        }
    }
}
