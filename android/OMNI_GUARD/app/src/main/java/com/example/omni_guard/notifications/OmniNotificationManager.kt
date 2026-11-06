package com.example.omni_guard.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.omni_guard.MainActivity
import com.example.omni_guard.data.ThreatLevel
import com.example.omni_guard.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmniNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var mediaPlayer: MediaPlayer? = null
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                "OMNI_GUARD Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Security system alerts and status"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showThreatAlert(threat: ThreatLevel) {
        val title: String
        val message: String
        val priority: Int

        stopAlarm()

        when (threat) {
            ThreatLevel.WARNING -> {
                title = "SYSTEM WARNING"
                message = "Potential threat detected!"
                priority = NotificationCompat.PRIORITY_HIGH
                vibrate(longArrayOf(0, 500, 200, 500))
            }
            ThreatLevel.INTRUDER -> {
                title = "INTRUDER ALERT"
                message = "Unauthorized access detected!"
                priority = NotificationCompat.PRIORITY_MAX
                vibrate(longArrayOf(0, 1000, 500, 1000))
            }
            ThreatLevel.FINAL -> {
                title = "EMERGENCY"
                message = "CRITICAL BREACH! ACTION REQUIRED"
                priority = NotificationCompat.PRIORITY_MAX
                vibrate(longArrayOf(0, 2000, 500, 2000))
                startAlarm()
            }
            else -> return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))

        notificationManager.notify(Constants.NOTIFICATION_ID, builder.build())
    }

    private fun startAlarm() {
        try {
            val alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alert)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopAlarm() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun vibrate(pattern: LongArray) {
        val sharedPrefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        if (!sharedPrefs.getBoolean(Constants.PREF_VIBRATION_ON, true)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}
