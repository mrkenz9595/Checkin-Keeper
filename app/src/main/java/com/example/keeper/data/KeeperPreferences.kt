package com.example.keeper.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object KeeperPreferences {
    private const val PREF_NAME = "checkin_keeper_prefs"

    private const val KEY_KEEP_ALIVE_ENABLED = "key_keep_alive_enabled"
    private const val KEY_OPERATION_MODE = "key_operation_mode"
    private const val KEY_INTERVAL_MINUTES = "key_interval_minutes"
    private const val KEY_TOTAL_TRIGGERS = "key_total_triggers"
    private const val KEY_LAST_TRIGGER_TIME = "key_last_trigger_time"
    private const val KEY_LAST_TRIGGER_SUCCESS = "key_last_trigger_success"
    private const val KEY_LAST_TRIGGER_MESSAGE = "key_last_trigger_message"
    private const val KEY_NEXT_TRIGGER_TIME = "key_next_trigger_time"
    private const val KEY_RECENT_LOGS = "key_recent_logs"

    const val MODE_FOREGROUND_SERVICE = "foreground_service"
    const val MODE_ALARM_ONLY = "alarm_only"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isKeepAliveEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_KEEP_ALIVE_ENABLED, false)
    }

    fun setKeepAliveEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_KEEP_ALIVE_ENABLED, enabled).apply()
    }

    fun getOperationMode(context: Context): String {
        return getPrefs(context).getString(KEY_OPERATION_MODE, MODE_FOREGROUND_SERVICE)
            ?: MODE_FOREGROUND_SERVICE
    }

    fun setOperationMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_OPERATION_MODE, mode).apply()
    }

    fun getIntervalMinutes(context: Context): Int {
        return getPrefs(context).getInt(KEY_INTERVAL_MINUTES, 10)
    }

    fun setIntervalMinutes(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt(KEY_INTERVAL_MINUTES, minutes).apply()
    }

    fun getTotalTriggers(context: Context): Int {
        return getPrefs(context).getInt(KEY_TOTAL_TRIGGERS, 0)
    }

    fun getLastTriggerTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_TRIGGER_TIME, 0L)
    }

    fun isLastTriggerSuccess(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_LAST_TRIGGER_SUCCESS, false)
    }

    fun getLastTriggerMessage(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_TRIGGER_MESSAGE, "") ?: ""
    }

    fun getNextTriggerTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_NEXT_TRIGGER_TIME, 0L)
    }

    fun setNextTriggerTime(context: Context, timestamp: Long) {
        getPrefs(context).edit().putLong(KEY_NEXT_TRIGGER_TIME, timestamp).apply()
    }

    fun recordTriggerEvent(
        context: Context,
        success: Boolean,
        message: String,
        networkType: String
    ) {
        val prefs = getPrefs(context)
        val currentTotal = prefs.getInt(KEY_TOTAL_TRIGGERS, 0) + 1
        val now = System.currentTimeMillis()

        val log = TriggerLog(
            timestamp = now,
            success = success,
            message = message,
            networkType = networkType
        )

        // Read and update recent logs list
        val logs = getRecentLogs(context).toMutableList()
        logs.add(0, log)
        if (logs.size > 25) {
            logs.removeAt(logs.size - 1)
        }

        val jsonArray = JSONArray()
        for (item in logs) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("timestamp", item.timestamp)
                put("success", item.success)
                put("message", item.message)
                put("networkType", item.networkType)
            }
            jsonArray.put(obj)
        }

        prefs.edit()
            .putInt(KEY_TOTAL_TRIGGERS, currentTotal)
            .putLong(KEY_LAST_TRIGGER_TIME, now)
            .putBoolean(KEY_LAST_TRIGGER_SUCCESS, success)
            .putString(KEY_LAST_TRIGGER_MESSAGE, message)
            .putString(KEY_RECENT_LOGS, jsonArray.toString())
            .apply()
    }

    fun getRecentLogs(context: Context): List<TriggerLog> {
        val raw = getPrefs(context).getString(KEY_RECENT_LOGS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<TriggerLog>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    TriggerLog(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        success = obj.optBoolean("success", false),
                        message = obj.optString("message", ""),
                        networkType = obj.optString("networkType", "Unknown")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun resetStatistics(context: Context) {
        getPrefs(context).edit()
            .putInt(KEY_TOTAL_TRIGGERS, 0)
            .putLong(KEY_LAST_TRIGGER_TIME, 0L)
            .putBoolean(KEY_LAST_TRIGGER_SUCCESS, false)
            .putString(KEY_LAST_TRIGGER_MESSAGE, "")
            .remove(KEY_RECENT_LOGS)
            .apply()
    }
}
