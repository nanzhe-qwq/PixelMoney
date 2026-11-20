package com.pixel.moneytracker

import android.app.NotificationManager
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.pixel.moneytracker.data.database.AppDatabase
import com.pixel.moneytracker.service.FloatingWindowService
import com.pixel.moneytracker.service.PendingPaymentNotifications
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayRollbackDeviceTest {
    @Test fun alipayUsesOverlayOnlyWithoutCreatingNotificationOrCandidate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val launch = instrumentation.uiAutomation.executeShellCommand("am start -W -n com.pixel.moneytracker/.MainActivity")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(launch).bufferedReader().use { it.readText() }
        assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)), 5000))
        val db = AppDatabase.getDatabase(context)
        val before = runBlocking { db.recordDao().getAllRecords().first() }
        val candidatesBefore = runBlocking { db.pendingPaymentDao().available(0).map { it.id }.toSet() }
        val manager = context.getSystemService(NotificationManager::class.java)
        val notificationsBefore = manager.activeNotifications.filter { it.notification.channelId == PendingPaymentNotifications.CHANNEL_ID }.map { it.key }.toSet()
        try {
            context.startService(Intent(context, FloatingWindowService::class.java).apply {
                putExtra(FloatingWindowService.EXTRA_AMOUNT, 12.39)
                putExtra(FloatingWindowService.EXTRA_SOURCE, "支付宝")
                putExtra(FloatingWindowService.EXTRA_NOTE, "AUTO_TEST_ROLLBACK_NOT_REAL_PAYMENT")
            })
            assertTrue("Legacy payment overlay must appear", device.wait(Until.hasObject(By.text("¥12.39")), 5000))
            assertEquals(candidatesBefore, runBlocking { db.pendingPaymentDao().available(0).map { it.id }.toSet() })
            assertEquals(notificationsBefore, manager.activeNotifications.filter { it.notification.channelId == PendingPaymentNotifications.CHANNEL_ID }.map { it.key }.toSet())
            device.findObject(By.text("忽略")).click()
            assertTrue(device.wait(Until.gone(By.text("记账 ✓")), 3000))
            assertEquals(before, runBlocking { db.recordDao().getAllRecords().first() })
        } finally {
            context.stopService(Intent(context, FloatingWindowService::class.java))
        }
    }
}
