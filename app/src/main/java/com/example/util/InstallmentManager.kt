package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.model.Installment
import com.example.data.model.InstallmentReminder
import com.example.receiver.InstallmentAlarmReceiver
import java.util.Calendar

object InstallmentManager {

    const val GENERAL_REMINDER_REQUEST_CODE = 90001
    const val CUSTOMER_REMINDER_REQUEST_OFFSET = 100000

    /**
     * Schedules a recurring/daily General Installment Reminder
     * Automatically cancels any previous general alarm to prevent duplicate notifications.
     */
    fun scheduleGeneralReminderAlarm(
        context: Context,
        hour: Int,
        minute: Int,
        daysBefore: Int = 1,
        daysAfter: Int = 3,
        scope: String = "جميع ما سبق",
        specificDate: Long = 0L,
        recurrence: String = "يومي",
        soundUri: String = "",
        soundTitle: String = "نغمة النظام الافتراضية",
        vibrationEnabled: Boolean = true
    ) {
        // Step 1: Always cancel old alarm before registering a new one
        cancelGeneralReminderAlarm(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val calendar = Calendar.getInstance()
        if (specificDate > 0L && recurrence == "مرة واحدة") {
            calendar.timeInMillis = specificDate
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            // If already in the past for "once", do not schedule
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                return
            }
        } else {
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                when (recurrence) {
                    "أسبوعي" -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
                    "شهري" -> calendar.add(Calendar.MONTH, 1)
                    else -> calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }

        val intent = Intent(context, InstallmentAlarmReceiver::class.java).apply {
            putExtra("isGeneralReminder", true)
            putExtra("hour", hour)
            putExtra("minute", minute)
            putExtra("daysBefore", daysBefore)
            putExtra("daysAfter", daysAfter)
            putExtra("generalScope", scope)
            putExtra("specificDate", specificDate)
            putExtra("recurrence", recurrence)
            putExtra("soundUri", soundUri)
            putExtra("soundTitle", soundTitle)
            putExtra("vibrationEnabled", vibrationEnabled)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            GENERAL_REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setAlarmExactOrIdle(alarmManager, calendar.timeInMillis, pendingIntent)
    }

    /**
     * Cancels general installment reminder alarm
     */
    fun cancelGeneralReminderAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, InstallmentAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            GENERAL_REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Schedules an individual Customer-Specific Installment Reminder
     * Automatically cancels any existing alarm for this reminder ID to avoid duplicates.
     */
    fun scheduleCustomerReminderAlarm(context: Context, reminder: InstallmentReminder) {
        // Step 1: Always cancel old alarm for this reminder first
        cancelCustomerReminder(context, reminder.id)

        if (reminder.scheduledAt <= System.currentTimeMillis()) {
            // Already in the past, no need to schedule future alarm
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, InstallmentAlarmReceiver::class.java).apply {
            putExtra("isCustomerReminder", true)
            putExtra("reminderId", reminder.id)
            putExtra("customerId", reminder.customerId ?: 0)
            putExtra("customerName", reminder.customerName ?: "")
            putExtra("remainingAmount", reminder.remainingAmount)
            putExtra("dueDate", reminder.dueDate)
            putExtra("currency", reminder.currency)
            putExtra("soundUri", reminder.soundUri)
            putExtra("soundTitle", reminder.soundTitle)
            putExtra("vibrationEnabled", reminder.vibrationEnabled)
            putExtra("recurrence", reminder.recurrence)
        }

        val requestCode = CUSTOMER_REMINDER_REQUEST_OFFSET + reminder.id
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setAlarmExactOrIdle(alarmManager, reminder.scheduledAt, pendingIntent)
    }

    /**
     * Cancels an individual Customer-Specific Installment Reminder
     */
    fun cancelCustomerReminder(context: Context, reminderId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, InstallmentAlarmReceiver::class.java)
        val requestCode = CUSTOMER_REMINDER_REQUEST_OFFSET + reminderId
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Schedules alarm for a raw installment record (backwards compatibility)
     */
    fun scheduleExactAlarm(context: Context, installment: Installment) {
        if (installment.isPaid || installment.dueDate <= System.currentTimeMillis()) {
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, InstallmentAlarmReceiver::class.java).apply {
            putExtra("installmentId", installment.id)
            putExtra("clientName", installment.clientName)
            putExtra("amount", installment.amount)
            putExtra("currency", installment.currency)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            installment.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setAlarmExactOrIdle(alarmManager, installment.dueDate, pendingIntent)
    }

    fun cancelAlarm(context: Context, installmentId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, InstallmentAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            installmentId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun setAlarmExactOrIdle(alarmManager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun calculateNextDueDate(currentDueDate: Long, recurrence: String): Long? {
        if (recurrence == "بدون تكرار") return null

        val cal = Calendar.getInstance().apply { timeInMillis = currentDueDate }
        when (recurrence) {
            "يومي" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "أسبوعي" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            "شهري" -> cal.add(Calendar.MONTH, 1)
            "سنوي" -> cal.add(Calendar.YEAR, 1)
            else -> return null
        }
        return cal.timeInMillis
    }
}
