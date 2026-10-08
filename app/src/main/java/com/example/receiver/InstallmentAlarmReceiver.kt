package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.database.AppDatabase
import com.example.data.model.InstallmentReminder
import com.example.data.model.InstallmentReminderStatus
import com.example.data.model.InstallmentReminderType
import com.example.util.DateTimeUtils
import com.example.util.FormatUtils
import com.example.util.InstallmentManager
import com.example.util.SoundHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class InstallmentAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isGeneralReminder = intent.getBooleanExtra("isGeneralReminder", false)
                val isCustomerReminder = intent.getBooleanExtra("isCustomerReminder", false)

                when {
                    isGeneralReminder -> {
                        handleGeneralReminder(context, intent)
                    }
                    isCustomerReminder -> {
                        handleCustomerReminder(context, intent)
                    }
                    else -> {
                        handleLegacyInstallmentReminder(context, intent)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleGeneralReminder(context: Context, intent: Intent) {
        val db = AppDatabase.getDatabase(context)
        val settings = db.storeSettingsDao().getSettings() ?: return
        if (!settings.isGeneralInstallmentReminderEnabled) {
            return
        }

        val hour = intent.getIntExtra("hour", settings.generalReminderHour)
        val minute = intent.getIntExtra("minute", settings.generalReminderMinute)
        val daysBefore = intent.getIntExtra("daysBefore", settings.generalReminderDaysBeforeDue)
        val daysAfter = intent.getIntExtra("daysAfter", settings.generalReminderDaysAfterOverdue)
        val scope = intent.getStringExtra("generalScope") ?: settings.generalReminderScope
        val recurrence = intent.getStringExtra("recurrence") ?: settings.generalAlarmRecurrence
        val soundUri = intent.getStringExtra("soundUri") ?: settings.generalSoundUri
        val soundTitle = intent.getStringExtra("soundTitle") ?: settings.generalSoundTitle
        val vibrationEnabled = intent.getBooleanExtra("vibrationEnabled", settings.generalVibrationEnabled)

        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        // Start of today (00:00:00)
        calendar.timeInMillis = now
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis

        // End of today (23:59:59)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val endOfToday = calendar.timeInMillis

        val upcomingLimit = endOfToday + (daysBefore.coerceAtLeast(1) * 86_400_000L)

        val allInstallments = db.installmentDao().getAllInstallments()

        // Filter installments based on scope with accurate daysAfter threshold
        val dueTodayList = allInstallments.filter { !it.isPaid && it.dueDate in startOfToday..endOfToday }
        val upcomingList = allInstallments.filter { !it.isPaid && it.dueDate in (endOfToday + 1)..upcomingLimit }
        val overdueList = if (daysAfter > 0) {
            val overdueThreshold = startOfToday - (daysAfter * 86_400_000L)
            allInstallments.filter { !it.isPaid && it.dueDate < startOfToday && it.dueDate >= overdueThreshold }
        } else {
            allInstallments.filter { !it.isPaid && it.dueDate < startOfToday }
        }

        val activeList = when (scope) {
            "أقساط مستحقة اليوم" -> dueTodayList
            "أقساط ستستحق قريبًا" -> upcomingList
            "أقساط متأخرة" -> overdueList
            else -> (dueTodayList + upcomingList + overdueList).distinctBy { it.id }
        }

        if (activeList.isNotEmpty() || overdueList.isNotEmpty()) {
            val dueCount = activeList.size
            val defaultCurr = settings.currency.ifBlank { "الريال اليمني" }

            // Group totals per currency to avoid mixing currencies
            val totalsByCurrency = activeList.groupBy { it.currency.ifBlank { defaultCurr } }
                .mapValues { entry -> entry.value.sumOf { (it.amount - it.paidAmount).coerceAtLeast(0.0) } }

            val breakdownText = totalsByCurrency.entries.joinToString(" | ") { (curr, sum) ->
                "${FormatUtils.formatAmount(sum)} ${FormatUtils.formatCurrencyCompact(curr)}"
            }.ifBlank { "0 $defaultCurr" }

            val overdueCustomerCount = overdueList.map { it.clientId }.distinct().size
            val overdueTotalsByCurrency = overdueList.groupBy { it.currency.ifBlank { defaultCurr } }
                .mapValues { entry -> entry.value.sumOf { (it.amount - it.paidAmount).coerceAtLeast(0.0) } }
            val overdueAmount = overdueTotalsByCurrency.values.sum()
            val totalDueAmount = totalsByCurrency.values.sum()

            // Insert persistent reminder in Room so Home screen dialog and lists see it
            val reminder = InstallmentReminder(
                reminderType = InstallmentReminderType.GENERAL_INSTALLMENT,
                amount = totalDueAmount,
                remainingAmount = totalDueAmount,
                dueDate = endOfToday,
                createdAt = now,
                scheduledAt = now,
                status = InstallmentReminderStatus.NEW,
                title = "تنبيه الأقساط",
                message = "لديك $dueCount أقساط مستحقة: $breakdownText",
                dueCount = dueCount,
                overdueCount = overdueCustomerCount,
                overdueAmount = overdueAmount,
                currency = defaultCurr,
                generalScope = scope,
                notificationId = 90001,
                soundUri = soundUri,
                soundTitle = soundTitle,
                vibrationEnabled = vibrationEnabled,
                recurrence = recurrence
            )
            val insertedId = db.installmentReminderDao().insertReminder(reminder).toInt()

            showGeneralNotification(context, insertedId, dueCount, breakdownText, soundUri, vibrationEnabled)
        }

        // Reschedule next general reminder if not one-time
        if (recurrence != "مرة واحدة") {
            InstallmentManager.scheduleGeneralReminderAlarm(
                context = context,
                hour = hour,
                minute = minute,
                daysBefore = daysBefore,
                daysAfter = daysAfter,
                scope = scope,
                specificDate = 0L,
                recurrence = recurrence,
                soundUri = soundUri,
                soundTitle = soundTitle,
                vibrationEnabled = vibrationEnabled
            )
        }
    }

    private suspend fun handleCustomerReminder(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra("reminderId", 0)
        if (reminderId <= 0) return

        val db = AppDatabase.getDatabase(context)
        val reminder = db.installmentReminderDao().getReminderById(reminderId) ?: return

        // If already handled or cancelled, exit
        if (reminder.status == InstallmentReminderStatus.HANDLED || reminder.status == InstallmentReminderStatus.CANCELLED) {
            return
        }

        val settings = db.storeSettingsDao().getSettings()
        if (settings != null && !settings.isCustomerInstallmentReminderEnabled) {
            return
        }

        // Financial source of truth check: Partial payments & Fully paid logic
        var currentRemaining = reminder.remainingAmount
        var isFullyPaid = false

        if (reminder.invoiceId != null && reminder.invoiceId > 0) {
            val invoice = db.invoiceDao().getInvoiceById(reminder.invoiceId)
            if (invoice == null || invoice.remainingAmount <= 0.01) {
                isFullyPaid = true
            } else {
                currentRemaining = invoice.remainingAmount
            }
        } else if (reminder.installmentId != null && reminder.installmentId > 0) {
            val inst = db.installmentDao().getInstallmentById(reminder.installmentId)
            if (inst == null || inst.isPaid || (inst.amount - inst.paidAmount) <= 0.01) {
                isFullyPaid = true
            } else {
                currentRemaining = (inst.amount - inst.paidAmount).coerceAtLeast(0.0)
            }
        } else if (reminder.customerId != null && reminder.customerId > 0) {
            val client = db.clientDao().getClientById(reminder.customerId)
            if (client == null || client.balance <= 0.01) {
                isFullyPaid = true
            } else {
                currentRemaining = client.balance
            }
        }

        // If fully paid, mark handled immediately and do not show notification!
        if (isFullyPaid || currentRemaining <= 0.01) {
            db.installmentReminderDao().markReminderHandled(reminder.id, System.currentTimeMillis())
            return
        }

        val now = System.currentTimeMillis()
        val overdueDays = if (reminder.dueDate > 0 && now > reminder.dueDate) {
            ((now - reminder.dueDate) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
        } else 0

        val soundUri = intent.getStringExtra("soundUri")?.ifBlank { null }
            ?: reminder.soundUri.ifBlank { settings?.customerSoundUri ?: "" }
        val vibrationEnabled = intent.getBooleanExtra("vibrationEnabled", reminder.vibrationEnabled)
        val recurrence = intent.getStringExtra("recurrence") ?: reminder.recurrence

        // Update reminder with latest financial remaining amount & overdue days
        db.installmentReminderDao().updateReminder(
            reminder.copy(
                remainingAmount = currentRemaining,
                overdueDays = overdueDays,
                status = InstallmentReminderStatus.NEW
            )
        )

        showCustomerNotification(
            context = context,
            reminderId = reminder.id,
            customerId = reminder.customerId ?: 0,
            customerName = reminder.customerName ?: "العميل",
            remainingAmount = currentRemaining,
            currency = reminder.currency,
            dueDate = reminder.dueDate,
            overdueDays = overdueDays,
            soundUriString = soundUri,
            vibrationEnabled = vibrationEnabled
        )

        // Reschedule if recurrence is set
        if (recurrence != "مرة واحدة") {
            val nextScheduleTime = InstallmentManager.calculateNextDueDate(reminder.scheduledAt, recurrence)
            if (nextScheduleTime != null && nextScheduleTime > now) {
                val nextReminder = reminder.copy(
                    id = 0,
                    scheduledAt = nextScheduleTime,
                    remainingAmount = currentRemaining,
                    status = InstallmentReminderStatus.NEW
                )
                val newId = db.installmentReminderDao().insertReminder(nextReminder).toInt()
                InstallmentManager.scheduleCustomerReminderAlarm(
                    context,
                    nextReminder.copy(id = newId)
                )
            }
        }
    }

    private fun handleLegacyInstallmentReminder(context: Context, intent: Intent) {
        val installmentId = intent.getIntExtra("installmentId", 0)
        val clientName = intent.getStringExtra("clientName") ?: "العميل"
        val amount = intent.getDoubleExtra("amount", 0.0)
        val currency = intent.getStringExtra("currency") ?: "الريال اليمني"

        showNotification(context, installmentId, clientName, amount, currency)
    }

    private fun showGeneralNotification(
        context: Context,
        reminderId: Int,
        dueCount: Int,
        breakdownText: String,
        soundUriString: String = "",
        vibrationEnabled: Boolean = true
    ) {
        val channelId = "general_installment_alarm_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createAlarmNotificationChannel(
            notificationManager = notificationManager,
            context = context,
            channelId = channelId,
            channelName = "منبّه الأقساط العام",
            soundUriString = soundUriString,
            vibrationEnabled = vibrationEnabled
        )

        val soundUri = SoundHelper.resolveValidSoundUri(context, soundUriString)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openInstallments", true)
            putExtra("reminderId", reminderId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            90001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bigText = """
            لديك $dueCount أقساط مستحقة.
            المبالغ المستحقة حسب العملة:
            $breakdownText
        """.trimIndent()

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🔔 منبّه الأقساط المستحقة")
            .setContentText("لديك $dueCount أقساط مستحقة ($breakdownText)")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .apply {
                if (soundUri != android.net.Uri.EMPTY) {
                    setSound(soundUri)
                }
                if (vibrationEnabled) {
                    setVibrate(longArrayOf(0, 500, 250, 500))
                }
            }
            .build()

        notificationManager.notify(90001, notification)
    }

    private fun showCustomerNotification(
        context: Context,
        reminderId: Int,
        customerId: Int,
        customerName: String,
        remainingAmount: Double,
        currency: String,
        dueDate: Long,
        overdueDays: Int,
        soundUriString: String = "",
        vibrationEnabled: Boolean = true
    ) {
        val channelId = "customer_installment_alarm_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createAlarmNotificationChannel(
            notificationManager = notificationManager,
            context = context,
            channelId = channelId,
            channelName = "منبّه أقساط العملاء",
            soundUriString = soundUriString,
            vibrationEnabled = vibrationEnabled
        )

        val soundUri = SoundHelper.resolveValidSoundUri(context, soundUriString)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openClientProfile", true)
            putExtra("clientId", customerId)
            putExtra("reminderId", reminderId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            100000 + reminderId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedAmount = FormatUtils.formatAmount(remainingAmount)
        val dueDateStr = if (dueDate > 0) DateTimeUtils.formatDateOnly(dueDate) else ""

        val bigTextBuilder = StringBuilder()
        bigTextBuilder.append("العميل: $customerName\n")
        bigTextBuilder.append("المبلغ المستحق: $formattedAmount $currency\n")
        if (dueDateStr.isNotEmpty()) {
            bigTextBuilder.append("تاريخ الاستحقاق: $dueDateStr\n")
        }
        if (overdueDays > 0) {
            bigTextBuilder.append("متأخر بالسداد: $overdueDays أيام")
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🔔 منبّه قسط: $customerName")
            .setContentText("المبلغ المستحق: $formattedAmount $currency")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigTextBuilder.toString()))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .apply {
                if (soundUri != android.net.Uri.EMPTY) {
                    setSound(soundUri)
                }
                if (vibrationEnabled) {
                    setVibrate(longArrayOf(0, 500, 250, 500))
                }
            }
            .build()

        notificationManager.notify(100000 + reminderId, notification)
    }

    private fun showNotification(
        context: Context,
        installmentId: Int,
        clientName: String,
        amount: Double,
        currency: String
    ) {
        val channelId = "installment_reminders_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createAlarmNotificationChannel(notificationManager, context, channelId, "تذكيرات الأقساط المستحقة")

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openInstallments", true)
            putExtra("installmentId", installmentId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            installmentId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedAmount = FormatUtils.formatAmount(amount)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⏰ موعد قسط مستحق: $clientName")
            .setContentText("تذكرة بموعد قسط مبلغ $formattedAmount $currency للعميل $clientName.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(installmentId, notification)
    }

    private fun createAlarmNotificationChannel(
        notificationManager: NotificationManager,
        context: Context,
        channelId: String,
        channelName: String,
        soundUriString: String = "",
        vibrationEnabled: Boolean = true
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = SoundHelper.resolveValidSoundUri(context, soundUriString)
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "قناة إشعارات للتنبيه بمواعيد استحقاق أقساط العملاء"
                if (soundUri != android.net.Uri.EMPTY) {
                    setSound(soundUri, audioAttributes)
                }
                enableVibration(vibrationEnabled)
                if (vibrationEnabled) {
                    vibrationPattern = longArrayOf(0, 500, 250, 500)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
