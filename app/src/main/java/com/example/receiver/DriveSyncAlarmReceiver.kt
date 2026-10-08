package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.util.GoogleDriveBackupManager
import com.example.util.GoogleDriveSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DriveSyncAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER_DRIVE_SYNC = "com.example.action.TRIGGER_DRIVE_SYNC"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val driveManager = GoogleDriveBackupManager(appContext)
        if (!driveManager.isSyncEnabled() || !GoogleDriveSyncScheduler.isScheduleEnabled(appContext)) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check and trigger sync or queue catch-up for internet
                GoogleDriveSyncScheduler.checkAndTriggerCatchUpIfNeeded(appContext)
                // Schedule the next alarm cycle
                GoogleDriveSyncScheduler.scheduleNextAlarm(appContext)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
