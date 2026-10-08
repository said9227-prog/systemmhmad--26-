package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.receiver.DriveSyncAlarmReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object GoogleDriveSyncScheduler {

    private const val PREFS_NAME = "drive_sync_schedule_prefs"
    const val KEY_SCHEDULE_ENABLED = "drive_schedule_enabled"
    const val KEY_SCHEDULE_FREQUENCY = "drive_schedule_frequency" // DAILY, WEEKLY, MONTHLY
    const val KEY_SCHEDULE_HOUR = "drive_schedule_hour"
    const val KEY_SCHEDULE_MINUTE = "drive_schedule_minute"
    const val KEY_SCHEDULE_DAY_OF_WEEK = "drive_schedule_day_of_week" // Calendar.FRIDAY = 6
    const val KEY_SCHEDULE_DAY_OF_MONTH = "drive_schedule_day_of_month" // 1..28
    const val KEY_LAST_SUCCESS_PERIOD_KEY = "drive_last_success_period_key"
    const val KEY_LAST_SUCCESS_TIMESTAMP = "drive_last_success_timestamp"

    private const val ALARM_REQUEST_CODE = 9301
    const val CATCH_UP_WORK_NAME = "GoogleDriveCatchUpSync"

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isScheduleEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_SCHEDULE_ENABLED, true)

    fun setScheduleEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SCHEDULE_ENABLED, enabled).apply()
        if (enabled) {
            scheduleNextAlarm(context)
            checkAndTriggerCatchUpIfNeeded(context)
        } else {
            cancelSchedule(context)
        }
    }

    fun getFrequency(context: Context): String =
        getPrefs(context).getString(KEY_SCHEDULE_FREQUENCY, "DAILY") ?: "DAILY"

    fun setFrequency(context: Context, frequency: String) {
        getPrefs(context).edit().putString(KEY_SCHEDULE_FREQUENCY, frequency).apply()
        scheduleNextAlarm(context)
        checkAndTriggerCatchUpIfNeeded(context)
    }

    fun getScheduleHour(context: Context): Int =
        getPrefs(context).getInt(KEY_SCHEDULE_HOUR, 7) // Default 7 AM

    fun getScheduleMinute(context: Context): Int =
        getPrefs(context).getInt(KEY_SCHEDULE_MINUTE, 30) // Default 30 minutes (07:30)

    fun setScheduleTime(context: Context, hour: Int, minute: Int) {
        getPrefs(context).edit()
            .putInt(KEY_SCHEDULE_HOUR, hour)
            .putInt(KEY_SCHEDULE_MINUTE, minute)
            .apply()
        scheduleNextAlarm(context)
        checkAndTriggerCatchUpIfNeeded(context)
    }

    fun getScheduleDayOfWeek(context: Context): Int =
        getPrefs(context).getInt(KEY_SCHEDULE_DAY_OF_WEEK, Calendar.FRIDAY)

    fun setScheduleDayOfWeek(context: Context, dayOfWeek: Int) {
        getPrefs(context).edit().putInt(KEY_SCHEDULE_DAY_OF_WEEK, dayOfWeek).apply()
        scheduleNextAlarm(context)
        checkAndTriggerCatchUpIfNeeded(context)
    }

    fun getScheduleDayOfMonth(context: Context): Int =
        getPrefs(context).getInt(KEY_SCHEDULE_DAY_OF_MONTH, 1)

    fun setScheduleDayOfMonth(context: Context, dayOfMonth: Int) {
        getPrefs(context).edit().putInt(KEY_SCHEDULE_DAY_OF_MONTH, dayOfMonth).apply()
        scheduleNextAlarm(context)
        checkAndTriggerCatchUpIfNeeded(context)
    }

    fun getLastSuccessPeriodKey(context: Context): String? =
        getPrefs(context).getString(KEY_LAST_SUCCESS_PERIOD_KEY, null)

    fun getLastSuccessTimestamp(context: Context): Long =
        getPrefs(context).getLong(KEY_LAST_SUCCESS_TIMESTAMP, 0L)

    /**
     * Unique period key to enforce idempotence and prevent duplicates within the same period.
     */
    fun getCurrentPeriodKey(frequency: String, date: Date = Date()): String {
        return when (frequency.uppercase(Locale.ENGLISH)) {
            "WEEKLY" -> {
                val cal = Calendar.getInstance().apply { time = date }
                val year = cal.get(Calendar.YEAR)
                val week = cal.get(Calendar.WEEK_OF_YEAR)
                String.format(Locale.ENGLISH, "%04d-W%02d", year, week)
            }
            "MONTHLY" -> {
                SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(date)
            }
            else -> {
                // DAILY
                SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(date)
            }
        }
    }

    fun recordSuccessForCurrentPeriod(context: Context) {
        val freq = getFrequency(context)
        val periodKey = getCurrentPeriodKey(freq)
        val now = System.currentTimeMillis()
        getPrefs(context).edit()
            .putString(KEY_LAST_SUCCESS_PERIOD_KEY, periodKey)
            .putLong(KEY_LAST_SUCCESS_TIMESTAMP, now)
            .apply()

        // Cancel any pending catch-up work since we succeeded for this period
        try {
            WorkManager.getInstance(context).cancelUniqueWork(CATCH_UP_WORK_NAME)
        } catch (_: Exception) {}
    }

    /**
     * Checks whether a sync is needed for the current period and has passed its scheduled trigger.
     */
    fun isSyncNeededForCurrentPeriod(context: Context): Boolean {
        val driveManager = GoogleDriveBackupManager(context)
        if (!driveManager.isSyncEnabled()) return false
        if (!isScheduleEnabled(context)) return false
        val email = driveManager.getSavedAccountEmail()
        if (email.isNullOrBlank()) return false

        val freq = getFrequency(context)
        val currentPeriodKey = getCurrentPeriodKey(freq)
        val lastSuccessKey = getLastSuccessPeriodKey(context)

        // If already succeeded for this period, do not duplicate!
        if (lastSuccessKey == currentPeriodKey) {
            return false
        }

        // Check if the scheduled time has arrived/passed for this period
        val now = Calendar.getInstance()
        val hour = getScheduleHour(context)
        val minute = getScheduleMinute(context)

        return when (freq.uppercase(Locale.ENGLISH)) {
            "DAILY" -> {
                val scheduledTimeToday = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                now.timeInMillis >= scheduledTimeToday.timeInMillis
            }
            "WEEKLY" -> {
                val targetDayOfWeek = getScheduleDayOfWeek(context)
                val currentDayOfWeek = now.get(Calendar.DAY_OF_WEEK)
                if (currentDayOfWeek > targetDayOfWeek) {
                    true
                } else if (currentDayOfWeek == targetDayOfWeek) {
                    val scheduledTimeToday = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    now.timeInMillis >= scheduledTimeToday.timeInMillis
                } else {
                    false
                }
            }
            "MONTHLY" -> {
                val targetDayOfMonth = getScheduleDayOfMonth(context)
                val currentDayOfMonth = now.get(Calendar.DAY_OF_MONTH)
                if (currentDayOfMonth > targetDayOfMonth) {
                    true
                } else if (currentDayOfMonth == targetDayOfMonth) {
                    val scheduledTimeToday = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    now.timeInMillis >= scheduledTimeToday.timeInMillis
                } else {
                    false
                }
            }
            else -> false
        }
    }

    fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        }
        return true
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Checks if catch-up sync is needed.
     * If internet is available, triggers immediately.
     * If internet is NOT available, enqueues WorkManager task with NetworkType.CONNECTED constraint!
     * The WorkManager task will run in the background the second the device connects to the internet!
     */
    fun checkAndTriggerCatchUpIfNeeded(context: Context) {
        if (!isSyncNeededForCurrentPeriod(context)) return

        if (isNetworkConnected(context)) {
            GoogleDriveSyncWorker.enqueueInstantSync(context, isCatchUp = true)
        } else {
            GoogleDriveSyncWorker.enqueueCatchUpSync(context)
        }
    }

    /**
     * Schedules the next exact alarm using AlarmManager.
     */
    fun scheduleNextAlarm(context: Context) {
        val driveManager = GoogleDriveBackupManager(context)
        if (!driveManager.isSyncEnabled() || !isScheduleEnabled(context)) {
            cancelSchedule(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DriveSyncAlarmReceiver::class.java).apply {
            action = DriveSyncAlarmReceiver.ACTION_TRIGGER_DRIVE_SYNC
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = calculateNextTriggerTime(context)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback non-exact
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    fun cancelSchedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DriveSyncAlarmReceiver::class.java).apply {
            action = DriveSyncAlarmReceiver.ACTION_TRIGGER_DRIVE_SYNC
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        try {
            WorkManager.getInstance(context).cancelUniqueWork(CATCH_UP_WORK_NAME)
        } catch (_: Exception) {}
    }

    private fun calculateNextTriggerTime(context: Context): Long {
        val now = Calendar.getInstance()
        val freq = getFrequency(context)
        val hour = getScheduleHour(context)
        val minute = getScheduleMinute(context)

        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (freq.uppercase(Locale.ENGLISH)) {
            "DAILY" -> {
                if (!target.after(now)) {
                    target.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            "WEEKLY" -> {
                val targetDayOfWeek = getScheduleDayOfWeek(context)
                target.set(Calendar.DAY_OF_WEEK, targetDayOfWeek)
                if (!target.after(now)) {
                    target.add(Calendar.WEEK_OF_YEAR, 1)
                }
            }
            "MONTHLY" -> {
                val targetDayOfMonth = getScheduleDayOfMonth(context).coerceAtMost(28)
                target.set(Calendar.DAY_OF_MONTH, targetDayOfMonth)
                if (!target.after(now)) {
                    target.add(Calendar.MONTH, 1)
                }
            }
            else -> {
                if (!target.after(now)) {
                    target.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }

        return target.timeInMillis
    }

    fun getNextSyncTimeDescription(context: Context): String {
        val freq = getFrequency(context)
        val hour = getScheduleHour(context)
        val minute = getScheduleMinute(context)
        val isAm = hour < 12
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val timeStr = String.format(Locale.getDefault(), "%02d:%02d %s", displayHour, minute, if (isAm) "صباحاً" else "مساءً")

        return when (freq.uppercase(Locale.ENGLISH)) {
            "WEEKLY" -> {
                val dayName = when (getScheduleDayOfWeek(context)) {
                    Calendar.SATURDAY -> "السبت"
                    Calendar.SUNDAY -> "الأحد"
                    Calendar.MONDAY -> "الإثنين"
                    Calendar.TUESDAY -> "الثلاثاء"
                    Calendar.WEDNESDAY -> "الأربعاء"
                    Calendar.THURSDAY -> "الخميس"
                    else -> "الجمعة"
                }
                "أسبوعياً كل يوم $dayName في $timeStr"
            }
            "MONTHLY" -> {
                val dayOfMonth = getScheduleDayOfMonth(context)
                "شهرياً في اليوم $dayOfMonth من الشهر في $timeStr"
            }
            else -> "يومياً في الساعة $timeStr"
        }
    }
}
