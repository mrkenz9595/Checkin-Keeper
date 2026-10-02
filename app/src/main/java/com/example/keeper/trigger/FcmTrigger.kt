package com.example.keeper.trigger

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.util.Log

data class TriggerResult(
    val success: Boolean,
    val message: String,
    val networkType: String,
    val timestamp: Long = System.currentTimeMillis()
)

object FcmTrigger {
    private const val TAG = "FcmTrigger"
    private const val WAKELOCK_TAG = "CheckinKeeper:FcmWakeLock"
    private const val WAKELOCK_TIMEOUT_MS = 5000L

    const val ACTION_MCS_HEARTBEAT = "com.google.android.intent.action.MCS_HEARTBEAT"
    const val ACTION_GCM_RECONNECT = "com.google.android.intent.action.GCM_RECONNECT"
    const val GMS_PACKAGE = "com.google.android.gms"

    /**
     * Determines whether the device currently has an active Internet connection,
     * and returns a human-readable network type description.
     */
    fun getNetworkStatus(context: Context): Pair<Boolean, String> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return Pair(false, "Không có ConnectivityManager")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = cm.activeNetwork ?: return Pair(false, "Không có mạng")
            val capabilities = cm.getNetworkCapabilities(activeNetwork)
                ?: return Pair(false, "Không có kết nối")

            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (!hasInternet) {
                return Pair(false, "Mạng không có Internet")
            }

            val typeStr = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dữ liệu di động"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "Mạng khác"
            }
            return Pair(true, typeStr)
        } else {
            @Suppress("DEPRECATION")
            val netInfo = cm.activeNetworkInfo
            @Suppress("DEPRECATION")
            val isConnected = netInfo != null && netInfo.isConnected
            @Suppress("DEPRECATION")
            val typeStr = if (isConnected) {
                when (netInfo?.type) {
                    ConnectivityManager.TYPE_WIFI -> "Wi-Fi"
                    ConnectivityManager.TYPE_MOBILE -> "Dữ liệu di động"
                    else -> "Mạng khác"
                }
            } else "Không có mạng"
            return Pair(isConnected, typeStr)
        }
    }

    /**
     * Sends wakeup broadcast intents to Google Play Services (GMS) to trigger FCM reconnection.
     * Protected by a temporary WakeLock (PARTIAL_WAKE_LOCK, 5s timeout) to ensure CPU stays awake.
     */
    fun forceReconnect(context: Context): TriggerResult {
        Log.d(TAG, "Starting FcmTrigger.forceReconnect...")

        val (hasNetwork, networkType) = getNetworkStatus(context)
        if (!hasNetwork) {
            Log.w(TAG, "No internet connectivity. Skipping FCM reconnect.")
            return TriggerResult(
                success = false,
                message = "Bỏ qua: Không có kết nối Internet",
                networkType = networkType
            )
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)?.apply {
            setReferenceCounted(false)
        }

        return try {
            // 1. Acquire WakeLock with a maximum 5-second safety timeout
            wakeLock?.acquire(WAKELOCK_TIMEOUT_MS)
            Log.d(TAG, "Acquired WakeLock for $WAKELOCK_TIMEOUT_MS ms")

            // 2. Broadcast MCS_HEARTBEAT to com.google.android.gms
            val mcsIntent = Intent(ACTION_MCS_HEARTBEAT).apply {
                setPackage(GMS_PACKAGE)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(mcsIntent)

            // 3. Broadcast GCM_RECONNECT to com.google.android.gms
            val gcmIntent = Intent(ACTION_GCM_RECONNECT).apply {
                setPackage(GMS_PACKAGE)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(gcmIntent)

            Log.i(TAG, "Dispatched MCS_HEARTBEAT and GCM_RECONNECT to $GMS_PACKAGE successfully")
            TriggerResult(
                success = true,
                message = "Đã gửi tín hiệu GMS Reconnect & Heartbeat thành công",
                networkType = networkType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error while triggering FCM reconnect: ${e.message}", e)
            TriggerResult(
                success = false,
                message = "Lỗi: ${e.localizedMessage ?: "Ngoại lệ không xác định"}",
                networkType = networkType
            )
        } finally {
            // 4. Safely release WakeLock in finally block
            try {
                if (wakeLock != null && wakeLock.isHeld) {
                    wakeLock.release()
                    Log.d(TAG, "Released WakeLock safely")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception during WakeLock release: ${e.message}")
            }
        }
    }
}
