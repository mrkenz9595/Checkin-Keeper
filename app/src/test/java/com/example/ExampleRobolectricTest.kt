package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.keeper.data.KeeperPreferences
import com.example.keeper.trigger.FcmTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun testAppNameString() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Checkin Keeper", appName)
    }

    @Test
    fun testKeeperPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Test interval
        KeeperPreferences.setIntervalMinutes(context, 15)
        assertEquals(15, KeeperPreferences.getIntervalMinutes(context))

        // Test mode
        KeeperPreferences.setOperationMode(context, KeeperPreferences.MODE_FOREGROUND_SERVICE)
        assertEquals(KeeperPreferences.MODE_FOREGROUND_SERVICE, KeeperPreferences.getOperationMode(context))

        // Test trigger record
        KeeperPreferences.recordTriggerEvent(
            context = context,
            success = true,
            message = "Test message",
            networkType = "Wi-Fi"
        )
        assertTrue(KeeperPreferences.getTotalTriggers(context) > 0)
        assertTrue(KeeperPreferences.getLastTriggerTime(context) > 0)
        assertTrue(KeeperPreferences.isLastTriggerSuccess(context))
        assertEquals("Test message", KeeperPreferences.getLastTriggerMessage(context))

        // Test logs list
        val logs = KeeperPreferences.getRecentLogs(context)
        assertTrue(logs.isNotEmpty())
        assertEquals("Wi-Fi", logs[0].networkType)

        // Test reset
        KeeperPreferences.resetStatistics(context)
        assertEquals(0, KeeperPreferences.getTotalTriggers(context))
    }

    @Test
    fun testFcmTriggerActionConstants() {
        assertEquals("com.google.android.intent.action.MCS_HEARTBEAT", FcmTrigger.ACTION_MCS_HEARTBEAT)
        assertEquals("com.google.android.intent.action.GCM_RECONNECT", FcmTrigger.ACTION_GCM_RECONNECT)
        assertEquals("com.google.android.gms", FcmTrigger.GMS_PACKAGE)
    }
}
