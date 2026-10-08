package com.example.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.database.AppDatabase
import com.example.data.preferences.BackupPreferences
import kotlinx.coroutines.flow.first

class BackupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val backupPreferences = BackupPreferences(context)
            val settings = backupPreferences.backupSettingsFlow.first()
            if (!settings.isAutoBackupEnabled) {
                return Result.success()
            }

            val db = AppDatabase.getDatabase(context)
            val result = BackupExecutionCoordinator.executeBackup(
                context = context,
                db = db,
                overrideType = settings.backupType,
                isTriggeredByWorkManager = true
            )

            // Trigger Google Drive cloud sync if enabled
            val driveManager = GoogleDriveBackupManager(context)
            if (driveManager.isSyncEnabled()) {
                GoogleDriveSyncWorker.enqueueInstantSync(context)
            }

            if (result.success) {
                Result.success()
            } else {
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}

