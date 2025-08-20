package com.kajava.homesecurity

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kajava.homesecurity.models.AlarmMessage

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID_ALARMS = "alarm_notifications"
        const val CHANNEL_ID_SERVICE = "service_notifications"
        const val NOTIFICATION_ID_ALARM = 1001
        const val NOTIFICATION_ID_SERVICE = 1002
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Alarm notifications channel
            val alarmChannel = NotificationChannel(
                CHANNEL_ID_ALARMS,
                "Security Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for security alarm events"
                enableLights(true)
                enableVibration(true)
                setBypassDnd(true)
            }

            // Service notifications channel
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for background service status"
                enableLights(false)
                enableVibration(false)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(alarmChannel)
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    fun showAlarmNotification(alarmMessage: AlarmMessage) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, message, priority) = when (alarmMessage.state) {
            "triggered" -> Triple(
                "🚨 ALARM TRIGGERED!",
                if (alarmMessage.triggeredBy != null)
                    "Triggered by: ${alarmMessage.triggeredBy}"
                else "Security alarm has been triggered",
                NotificationCompat.PRIORITY_HIGH
            )
            "armed" -> Triple(
                "🛡️ System Armed",
                "Security system is now armed and monitoring",
                NotificationCompat.PRIORITY_DEFAULT
            )
            "disarmed" -> Triple(
                "✅ System Disarmed",
                "Security system has been disarmed",
                NotificationCompat.PRIORITY_DEFAULT
            )
            else -> Triple(
                "🔔 Security Alert",
                alarmMessage.message ?: "Security system state changed",
                NotificationCompat.PRIORITY_DEFAULT
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALARMS)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priority)
            .setCategory(
                if (alarmMessage.state == "triggered")
                    NotificationCompat.CATEGORY_ALARM
                else
                    NotificationCompat.CATEGORY_STATUS
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .apply {
                // Only add sound/vibration for triggered alarms
                if (alarmMessage.state == "triggered") {
                    setDefaults(NotificationCompat.DEFAULT_ALL)
                    setVibrate(longArrayOf(0, 500, 250, 500, 250, 500))
                } else {
                    setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                }
            }
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ALARM, notification)
        } catch (e: SecurityException) {
            // Handle case where notification permission is not granted
            android.util.Log.e("NotificationHelper", "Failed to show notification: ${e.message}")
        }
    }

    fun createServiceNotification(): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Home Security Monitor")
            .setContentText("Connected to security system")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun updateServiceNotification(isConnected: Boolean) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Home Security Monitor")
            .setContentText(if (isConnected) "Connected to security system" else "Disconnected - Retrying...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SERVICE, notification)
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "Failed to update service notification: ${e.message}")
        }
    }
}