package com.example.keeper.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.keeper.data.KeeperPreferences
import com.example.keeper.receiver.AlarmReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KeepAliveService onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "KeepAliveService onStartCommand: action=$action")

        when (action) {
            ACTION_START -> {
                startForegroundWithNotification()
                val intervalMinutes = KeeperPreferences.getIntervalMinutes(this)
                scheduleNextAlarm(this, intervalMinutes * 60 * 1000L)
            }
            ACTION_STOP -> {
                cancelAlarm(this)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_NOTIFICATION -> {
                val notification = buildNotification()
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, notification)
            }
            ACTION_TRIGGER_NOW -> {
                // Forward trigger action to AlarmReceiver
                val triggerIntent = Intent(this, AlarmReceiver::class.java).apply {
                    this.action = AlarmReceiver.ACTION_TRIGGER_NOW
                }
                sendBroadcast(triggerIntent)
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val count = KeeperPreferences.getTotalTriggers(this)
        val lastTime = KeeperPreferences.getLastTriggerTime(this)
        val interval = KeeperPreferences.getIntervalMinutes(this)

        val lastTimeFormatted = if (lastTime > 0) {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            sdf.format(Date(lastTime))
        } else {
            "Chưa chạy"
        }

        val contentText = "Chu kỳ: $interval phút | Đã chạy: $count lần | Gần nhất: $lastTimeFormatted"

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action trigger now
        val triggerNowIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_NOW
        }
        val triggerNowPendingIntent = PendingIntent.getBroadcast(
            this,
            2002,
            triggerNowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Checkin Keeper đang hoạt động")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$contentText\nĐang duy trì kết nối FCM ổn định."))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                R.drawable.ic_notification,
                "Kích hoạt ngay",
                triggerNowPendingIntent
            )

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "KeepAliveService onDestroy")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KeepAliveService"
        const val CHANNEL_ID = "fcm_keeper_channel"
        const val NOTIFICATION_ID = 101

        const val ACTION_START = "com.example.keeper.ACTION_START"
        const val ACTION_STOP = "com.example.keeper.ACTION_STOP"
        const val ACTION_UPDATE_NOTIFICATION = "com.example.keeper.ACTION_UPDATE_NOTIFICATION"
        const val ACTION_TRIGGER_NOW = "com.example.keeper.ACTION_TRIGGER_NOW"

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateNotification(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java).apply {
                action = ACTION_UPDATE_NOTIFICATION
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not update service notification: ${e.message}")
            }
        }

        /**
         * Schedules the next wake-up alarm using AlarmManager.
         * Checks canScheduleExactAlarms() on Android 12+ (API 31+).
         */
        fun scheduleNextAlarm(context: Context, intervalMillis: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_ALARM_TRIGGER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                AlarmReceiver.REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = System.currentTimeMillis() + intervalMillis
            KeeperPreferences.setNextTriggerTime(context, triggerAtMillis)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                        Log.d(TAG, "Alarm setExactAndAllowWhileIdle for $triggerAtMillis")
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                        Log.d(TAG, "Exact alarm permission denied. Used setAndAllowWhileIdle.")
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "Alarm setExactAndAllowWhileIdle for $triggerAtMillis")
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException scheduling exact alarm: ${e.message}. Falling back to standard alarm.", e)
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }

        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_ALARM_TRIGGER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                AlarmReceiver.REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "Alarm successfully cancelled.")
            }
            KeeperPreferences.setNextTriggerTime(context, 0L)
        }
    }
}
