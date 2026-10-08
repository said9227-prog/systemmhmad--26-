package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity

object SoundHelper {

    private var activeRingtone: Ringtone? = null

    /**
     * Resolves a validated sound Uri. Falls back gracefully to system default alarm or notification tone
     * if the saved Uri is invalid, empty, or inaccessible.
     */
    fun resolveValidSoundUri(context: Context, soundUriString: String): Uri {
        if (soundUriString.isNotBlank()) {
            try {
                val parsed = Uri.parse(soundUriString)
                // Verify if content can be opened
                context.contentResolver.openInputStream(parsed)?.use {
                    // accessible!
                    return parsed
                }
            } catch (e: Exception) {
                // Uri inaccessible or revoked permission -> fall back
            }
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: Uri.EMPTY
    }

    /**
     * Safely returns a human-readable title for the given sound Uri.
     */
    fun getRingtoneTitle(context: Context, soundUriString: String): String {
        if (soundUriString.isBlank()) {
            return "نغمة النظام الافتراضية"
        }
        return try {
            val uri = Uri.parse(soundUriString)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            val title = ringtone?.getTitle(context)
            if (!title.isNullOrBlank()) title else "نغمة مخصصة"
        } catch (e: Exception) {
            "نغمة النظام الافتراضية"
        }
    }

    /**
     * Plays a live preview of the selected ringtone.
     */
    @Synchronized
    fun playPreview(context: Context, soundUriString: String) {
        stopPreview()
        try {
            val uri = resolveValidSoundUri(context, soundUriString)
            if (uri != Uri.EMPTY) {
                val ringtone = RingtoneManager.getRingtone(context, uri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ringtone?.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                ringtone?.play()
                activeRingtone = ringtone
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Stops any currently playing preview.
     */
    @Synchronized
    fun stopPreview() {
        try {
            activeRingtone?.stop()
            activeRingtone = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun isPlaying(): Boolean {
        return activeRingtone?.isPlaying == true
    }

    /**
     * Triggers a preview vibration pattern.
     */
    fun vibratePreview(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 400, 200, 400), -1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Triggers a test notification without polluting the real financial database.
     */
    fun triggerTestNotification(
        context: Context,
        title: String = "🔔 اختبار منبّه الأقساط",
        message: String = "هذه معاينة تجريبية للتأكد من عمل الصوت والاهتزاز والإشعار بشكل سليم ومستقل.",
        soundUriString: String = "",
        vibrationEnabled: Boolean = true
    ) {
        val channelId = "test_installment_alarm_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val soundUri = resolveValidSoundUri(context, soundUriString)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                channelId,
                "اختبار منبّه الأقساط",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "قناة مخصصة لاختبار ومعاينة منبهات ونغمات الأقساط"
                if (soundUri != Uri.EMPTY) {
                    setSound(soundUri, audioAttributes)
                }
                enableVibration(vibrationEnabled)
                if (vibrationEnabled) {
                    vibrationPattern = longArrayOf(0, 500, 250, 500)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openInstallments", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            99999,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (soundUri != Uri.EMPTY) {
            builder.setSound(soundUri)
        }
        if (vibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 500, 250, 500))
        }

        notificationManager.notify(99999, builder.build())

        // Play preview audio alongside test notification if user is in-app
        playPreview(context, soundUriString)
        if (vibrationEnabled) {
            vibratePreview(context)
        }
    }
}
