package com.example.keeper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.keeper.data.KeeperPreferences
import com.example.keeper.service.KeepAliveService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d(TAG, "BootReceiver received action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val isEnabled = KeeperPreferences.isKeepAliveEnabled(context)
            val mode = KeeperPreferences.getOperationMode(context)
            Log.d(TAG, "Device booted. isEnabled=$isEnabled, mode=$mode")

            if (isEnabled) {
                if (mode == KeeperPreferences.MODE_FOREGROUND_SERVICE) {
                    KeepAliveService.start(context)
                } else {
                    val intervalMinutes = KeeperPreferences.getIntervalMinutes(context)
                    KeepAliveService.scheduleNextAlarm(context, intervalMinutes * 60 * 1000L)
                }
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
