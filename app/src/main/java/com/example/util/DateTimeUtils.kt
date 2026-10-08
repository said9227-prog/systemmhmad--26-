package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateTimeUtils {

    /**
     * Formats timestamp into 12-hour format with Arabic AM/PM (ص / م).
     * Example: "2026-07-23 02:30 م"
     */
    fun formatDateTime12h(timeMillis: Long = System.currentTimeMillis()): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm", Locale.ENGLISH)
        val amPmFormat = SimpleDateFormat("a", Locale.ENGLISH)
        val date = Date(timeMillis)
        val formattedDate = dateFormat.format(date)
        val amPm = if (amPmFormat.format(date).equals("AM", ignoreCase = true)) "ص" else "م"
        return "$formattedDate $amPm"
    }

    /**
     * Formats time only into 12-hour format.
     * Example: "02:30 م"
     */
    fun formatTime12h(timeMillis: Long = System.currentTimeMillis()): String {
        val timeFormat = SimpleDateFormat("hh:mm", Locale.ENGLISH)
        val amPmFormat = SimpleDateFormat("a", Locale.ENGLISH)
        val date = Date(timeMillis)
        val formattedTime = timeFormat.format(date)
        val amPm = if (amPmFormat.format(date).equals("AM", ignoreCase = true)) "ص" else "م"
        return "$formattedTime $amPm"
    }

    /**
     * Formats date only.
     * Example: "2026-07-23"
     */
    fun formatDateOnly(timeMillis: Long = System.currentTimeMillis()): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        return dateFormat.format(Date(timeMillis))
    }

    fun getStartOfDay(timeMillis: Long = System.currentTimeMillis()): Long {
        val cal = java.util.Calendar.getInstance().apply {
            this.timeInMillis = timeMillis
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getEndOfDay(timeMillis: Long = System.currentTimeMillis()): Long {
        val cal = java.util.Calendar.getInstance().apply {
            this.timeInMillis = timeMillis
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }
}

