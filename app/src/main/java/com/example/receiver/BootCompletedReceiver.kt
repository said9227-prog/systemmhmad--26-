package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.database.AppDatabase
import com.example.data.model.InstallmentReminderStatus
import com.example.data.model.InstallmentReminderType
import com.example.util.InstallmentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val appContext = context.applicationContext
                    val db = AppDatabase.getDatabase(appContext)
                    val settings = db.storeSettingsDao().getSettings()

                    // Reschedule General Reminder
                    if (settings != null && settings.isGeneralInstallmentReminderEnabled) {
                        InstallmentManager.scheduleGeneralReminderAlarm(
                            context = appContext,
                            hour = settings.generalReminderHour,
                            minute = settings.generalReminderMinute,
                            daysBefore = settings.generalReminderDaysBeforeDue,
                            daysAfter = settings.generalReminderDaysAfterOverdue,
                            scope = settings.generalReminderScope,
                            specificDate = settings.generalAlarmDate,
                            recurrence = settings.generalAlarmRecurrence,
                            soundUri = settings.generalSoundUri,
                            soundTitle = settings.generalSoundTitle,
                            vibrationEnabled = settings.generalVibrationEnabled
                        )
                    }

                    // Reschedule pending Customer Reminders
                    val now = System.currentTimeMillis()
                    val unhandled = db.installmentReminderDao().getUnhandledReminders()
                    unhandled.filter {
                        it.reminderType == InstallmentReminderType.CUSTOMER_INSTALLMENT &&
                        it.status == InstallmentReminderStatus.NEW &&
                        it.scheduledAt > now
                    }.forEach { reminder ->
                        InstallmentManager.scheduleCustomerReminderAlarm(appContext, reminder)
                    }

                    // Reschedule raw installments (backwards compatibility)
                    val installments = db.installmentDao().getAllInstallments()
                    installments.filter { !it.isPaid && it.dueDate > now }.forEach { installment ->
                        InstallmentManager.scheduleExactAlarm(appContext, installment)
                    }

                    // Reschedule Auto-Backup and Google Drive Sync
                    val backupPrefs = com.example.data.preferences.BackupPreferences(appContext)
                    val backupSettings = backupPrefs.backupSettingsFlow.first()
                    if (backupSettings.isAutoBackupEnabled) {
                        com.example.util.BackupScheduler.scheduleAutomaticBackup(
                            context = appContext,
                            enabled = true,
                            frequency = backupSettings.frequency,
                            hour = backupSettings.backupExecutionHour,
                            minute = backupSettings.backupExecutionMinute
                        )
                    }

                    val driveManager = com.example.util.GoogleDriveBackupManager(appContext)
                    if (driveManager.isSyncEnabled()) {
                        com.example.util.GoogleDriveSyncScheduler.scheduleNextAlarm(appContext)
                        com.example.util.GoogleDriveSyncScheduler.checkAndTriggerCatchUpIfNeeded(appContext)
                        com.example.util.GoogleDriveSyncWorker.enqueuePeriodicSync(appContext)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
