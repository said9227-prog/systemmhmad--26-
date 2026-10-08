package com.example.util

import android.content.Context
import androidx.work.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object ClientStatementExportScheduler {
    const val WORK_NAME = "AutomatedClientStatementExportWork"

    fun calculateDelayForDaily(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }

    fun calculateDelayForSpecificDate(dateStr: String, hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        return try {
            val parts = dateStr.split("-")
            val year = parts[0].toInt()
            val month = parts[1].toInt() - 1
            val day = parts[2].toInt()

            val target = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.before(now)) 0L else (target.timeInMillis - now.timeInMillis)
        } catch (e: Exception) {
            calculateDelayForDaily(hour, minute)
        }
    }

    fun calculateDelayForWeekly(dayOfWeek: Int, hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }

    fun calculateDelayForMonthly(dayOfMonth: Int, hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            val maxDayThisMonth = getActualMaximum(Calendar.DAY_OF_MONTH)
            set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceAtMost(maxDayThisMonth))
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.MONTH, 1)
                val maxDayNextMonth = getActualMaximum(Calendar.DAY_OF_MONTH)
                set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceAtMost(maxDayNextMonth))
            }
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }

    fun scheduleExport(
        context: Context,
        enabled: Boolean,
        frequency: String, // "DAILY", "SPECIFIC_DATE", "WEEKLY", "MONTHLY"
        hour: Int = 0,
        minute: Int = 0,
        specificDate: String = "", // "yyyy-MM-dd"
        dayOfWeek: Int = Calendar.THURSDAY, // 1..7 (Calendar.SUNDAY..SATURDAY)
        dayOfMonth: Int = 1
    ) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        when (frequency) {
            "SPECIFIC_DATE" -> {
                val delayMs = calculateDelayForSpecificDate(specificDate, hour, minute)
                val request = OneTimeWorkRequestBuilder<ClientStatementExportWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints)
                    .build()
                workManager.enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }
            "WEEKLY" -> {
                val delayMs = calculateDelayForWeekly(dayOfWeek, hour, minute)
                val request = PeriodicWorkRequestBuilder<ClientStatementExportWorker>(
                    7, TimeUnit.DAYS
                )
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints)
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }
            "MONTHLY" -> {
                val delayMs = calculateDelayForMonthly(dayOfMonth, hour, minute)
                val request = PeriodicWorkRequestBuilder<ClientStatementExportWorker>(
                    30, TimeUnit.DAYS
                )
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints)
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }
            else -> { // DAILY
                val delayMs = calculateDelayForDaily(hour, minute)
                val request = PeriodicWorkRequestBuilder<ClientStatementExportWorker>(
                    1, TimeUnit.DAYS
                )
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints)
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }
        }
    }

    fun getNextScheduleDescription(
        frequency: String,
        hour: Int,
        minute: Int,
        specificDate: String,
        dayOfWeek: Int,
        dayOfMonth: Int
    ): String {
        val hour12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        val amPm = if (hour < 12) "ص" else "م"
        val minStr = String.format(Locale.US, "%02d", minute)
        val timeLabel = "$hour12:$minStr $amPm"

        return when (frequency) {
            "SPECIFIC_DATE" -> {
                if (specificDate.isBlank()) "تاريخ محدد الساعة $timeLabel"
                else "في تاريخ $specificDate الساعة $timeLabel"
            }
            "WEEKLY" -> {
                val dayName = when (dayOfWeek) {
                    Calendar.SATURDAY -> "السبت"
                    Calendar.SUNDAY -> "الأحد"
                    Calendar.MONDAY -> "الإثنين"
                    Calendar.TUESDAY -> "الثلاثاء"
                    Calendar.WEDNESDAY -> "الأربعاء"
                    Calendar.THURSDAY -> "الخميس"
                    Calendar.FRIDAY -> "الجمعة"
                    else -> "الخميس"
                }
                "كل أسبوع (يوم $dayName) الساعة $timeLabel"
            }
            "MONTHLY" -> {
                val dayDesc = if (dayOfMonth == 1) "اليوم الأول" else "يوم $dayOfMonth"
                "كل شهر ($dayDesc من الشهر) الساعة $timeLabel"
            }
            else -> "يومياً الساعة $timeLabel"
        }
    }
}
