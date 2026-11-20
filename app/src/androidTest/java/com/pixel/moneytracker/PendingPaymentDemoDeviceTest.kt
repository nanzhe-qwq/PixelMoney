package com.pixel.moneytracker

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixel.moneytracker.service.PendingPaymentNotifications
import com.pixel.moneytracker.service.PendingPayments
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicitly selected preview only. Leaves one labelled synthetic notification, never saves a record. */
@RunWith(AndroidJUnit4::class)
class PendingPaymentDemoDeviceTest {
    @Test fun prepareOneSyntheticPreviewNotification() = runBlocking {
        assumeTrue("Explicit preview opt-in required",
            InstrumentationRegistry.getArguments().getString("prepareDemo") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        assertTrue("Existing notification permission required", manager.areNotificationsEnabled())
        val candidate = PendingPayments.store(context).create(
            12.34, "支付宝（演示）", "演示账单，不是真实消费，请点忽略", false)
        assertTrue(PendingPaymentNotifications(context).post(candidate))
        val deadline = android.os.SystemClock.uptimeMillis() + 5000
        while (android.os.SystemClock.uptimeMillis() < deadline && manager.activeNotifications.none { it.tag == candidate.id }) {
            android.os.SystemClock.sleep(50)
        }
        assertTrue(manager.activeNotifications.any { it.tag == candidate.id })
        println("DEMO_CANDIDATE_ID=" + candidate.id)
        println("DEMO_AMOUNT=12.34 SYNTHETIC_NOT_A_REAL_PAYMENT")
    }
}
