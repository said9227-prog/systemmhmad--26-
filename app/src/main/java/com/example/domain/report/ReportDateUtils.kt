package com.example.domain.report

import java.util.Calendar

enum class DateFilterType(val label: String) {
    TODAY("اليوم"),
    YESTERDAY("أمس"),
    THIS_WEEK("هذا الأسبوع"),
    LAST_WEEK("الأسبوع الماضي"),
    THIS_MONTH("هذا الشهر"),
    LAST_MONTH("الشهر الماضي"),
    THIS_YEAR("هذه السنة"),
    LAST_YEAR("السنة الماضية"),
    LAST_7_DAYS("آخر 7 أيام"),
    LAST_30_DAYS("آخر 30 يومًا"),
    LAST_90_DAYS("آخر 90 يومًا"),
    ALL_TIME("كل الأوقات"),
    CUSTOM("مخصص")
}

data class DateRange(val start: Long, val end: Long)

object ReportDateUtils {

    fun getRange(type: DateFilterType): DateRange {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis

        return when (type) {
            DateFilterType.TODAY -> {
                val start = startOfDay(cal)
                val end = endOfDay(cal)
                DateRange(start, end)
            }
            DateFilterType.YESTERDAY -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                val start = startOfDay(cal)
                val end = endOfDay(cal)
                DateRange(start, end)
            }
            DateFilterType.THIS_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val start = startOfDay(cal)
                cal.add(Calendar.DAY_OF_YEAR, 6)
                val end = endOfDay(cal)
                DateRange(start, end)
            }
            DateFilterType.LAST_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.add(Calendar.DAY_OF_YEAR, -7)
                val start = startOfDay(cal)
                cal.add(Calendar.DAY_OF_YEAR, 6)
                val end = endOfDay(cal)
                DateRange(start, end)
            }
            DateFilterType.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(cal)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                val end = endOfDay(cal)
                DateRange(start, end)
            }
            DateFilterType.LAST_MONTH -> {
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(cal)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                val end = endOfDay(cal)
                DateRange(start, end)
            }
            DateFilterType.THIS_YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                val start = startOfDay(cal)
                cal.set(Calendar.MONTH, Calendar.DECEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                val end = endOfDay(cal)
                DateRange(start, end)
            }
            DateFilterType.LAST_YEAR -> {
                cal.add(Calendar.YEAR, -1)
                cal.set(Calendar.DAY_OF_YEAR, 1)
                val start = startOfDay(cal)
                cal.set(Calendar.MONTH, Calendar.DECEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                val end = endOfDay(cal)
                DateRange(start, end)
            }
            DateFilterType.LAST_7_DAYS -> {
                val end = endOfDay(cal)
                cal.add(Calendar.DAY_OF_YEAR, -6)
                val start = startOfDay(cal)
                DateRange(start, end)
            }
            DateFilterType.LAST_30_DAYS -> {
                val end = endOfDay(cal)
                cal.add(Calendar.DAY_OF_YEAR, -29)
                val start = startOfDay(cal)
                DateRange(start, end)
            }
            DateFilterType.LAST_90_DAYS -> {
                val end = endOfDay(cal)
                cal.add(Calendar.DAY_OF_YEAR, -89)
                val start = startOfDay(cal)
                DateRange(start, end)
            }
            DateFilterType.ALL_TIME -> {
                DateRange(0L, Long.MAX_VALUE)
            }
            DateFilterType.CUSTOM -> {
                // Return today by default, caller should overwrite
                val start = startOfDay(cal)
                val end = endOfDay(cal)
                DateRange(start, end)
            }
        }
    }

    fun startOfDay(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun endOfDay(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        c.set(Calendar.MILLISECOND, 999)
        return c.timeInMillis
    }
}
