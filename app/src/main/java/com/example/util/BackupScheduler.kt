package com.example.util

import android.content.Context
import androidx.work.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object BackupScheduler {
    const val WORK_NAME = "AutomatedBackupWork"

    fun calculateInitialDelay(targetHour: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }

    fun scheduleAutomaticBackup(
        context: Context,
        enabled: Boolean,
        frequency: String = "DAILY", // DAILY or WEEKLY
        hour: Int = 2,
        minute: Int = 0
    ) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val repeatIntervalDays = if (frequency.equals("WEEKLY", ignoreCase = true)) 7L else 1L
        val initialDelayMs = calculateInitialDelay(hour, minute)

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(
            repeatIntervalDays, TimeUnit.DAYS
        )
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15, TimeUnit.MINUTES
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            backupRequest
        )
    }

    fun scheduleAutomaticBackup(
        context: Context,
        enabled: Boolean,
        intervalDays: Int,
        hour: Int,
        minute: Int
    ) {
        val freq = if (intervalDays >= 7) "WEEKLY" else "DAILY"
        scheduleAutomaticBackup(context, enabled, freq, hour, minute)
    }

    fun scheduleAutomaticBackupWithTime(
        context: Context,
        enabled: Boolean,
        intervalDays: Int = 1,
        hour: Int = 2,
        minute: Int = 0
    ) {
        scheduleAutomaticBackup(
            context = context,
            enabled = enabled,
            frequency = if (intervalDays >= 7) "WEEKLY" else "DAILY",
            hour = hour,
            minute = minute
        )
    }

    fun getNextBackupTimeString(hour: Int, minute: Int, intervalDays: Int = 1): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val isToday = !target.before(now)
        val dateLabel = if (isToday) "اليوم" else if (intervalDays == 1) "غداً" else "بعد $intervalDays أيام"

        val hour12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        val amPm = if (hour < 12) "ص" else "م"
        val minStr = String.format(Locale.US, "%02d", minute)

        return "$dateLabel الساعة $hour12:$minStr $amPm"
    }
}
