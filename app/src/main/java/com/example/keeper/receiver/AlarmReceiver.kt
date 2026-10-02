package com.example.keeper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.keeper.data.KeeperPreferences
import com.example.keeper.service.KeepAliveService
import com.example.keeper.trigger.FcmTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d(TAG, "AlarmReceiver onReceive action: $action")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // 1. Force reconnect FCM
                val result = FcmTrigger.forceReconnect(context)
                Log.d(TAG, "Trigger result: success=${result.success}, msg=${result.message}")

                // 2. Record trigger stats and history into SharedPreferences
                KeeperPreferences.recordTriggerEvent(
                    context = context,
                    success = result.success,
                    message = result.message,
                    networkType = result.networkType
                )

                // 3. Update notification in running service
                KeepAliveService.updateNotification(context)

                // 4. Schedule next alarm loop if keeper is still active
                val isEnabled = KeeperPreferences.isKeepAliveEnabled(context)
                if (isEnabled) {
                    val intervalMinutes = KeeperPreferences.getIntervalMinutes(context)
                    val intervalMillis = intervalMinutes * 60 * 1000L
                    KeepAliveService.scheduleNextAlarm(context, intervalMillis)
                    Log.d(TAG, "Scheduled next alarm in $intervalMinutes minutes ($intervalMillis ms)")
                } else {
                    Log.d(TAG, "Keeper is disabled. Skipping next alarm schedule.")
                }

                // 5. Broadcast to notify UI of trigger update
                val updateIntent = Intent(ACTION_DATA_UPDATED).apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(updateIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Exception during AlarmReceiver execution: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
        const val ACTION_ALARM_TRIGGER = "com.example.keeper.ACTION_ALARM_TRIGGER"
        const val ACTION_TRIGGER_NOW = "com.example.keeper.ACTION_TRIGGER_NOW"
        const val ACTION_DATA_UPDATED = "com.example.keeper.ACTION_DATA_UPDATED"
        const val REQUEST_CODE = 2001
    }
}
