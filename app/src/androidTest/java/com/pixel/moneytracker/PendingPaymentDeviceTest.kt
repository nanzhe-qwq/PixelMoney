package com.pixel.moneytracker

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.pixel.moneytracker.data.database.AppDatabase
import com.pixel.moneytracker.service.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Synthetic candidates only. No payment, adb, permission grants, or diagnostic flag dependency.
 *
 * 4.1.1 起确认入口换成了独立透明的 PaymentPopupActivity，旧的通知点击入口
 * PaymentConfirmActivity 在 AndroidManifest 里显式 `enabled="false"`。这些用例仍然去点
 * 那条已停用的通知，PendingIntent.send() 必然抛 CanceledException(-92)，测的是关闭的代码。
 * 保留用例备将来重新启用通知兜底时复用，但不参与回归。
 */
@Ignore("PaymentConfirmActivity 自 4.1.1 起 enabled=false；通知候选入口已停用")
@RunWith(AndroidJUnit4::class)
class PendingPaymentDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val marker = "AUTO_TEST_PENDING_" + System.nanoTime()
    private val ids = mutableSetOf<String>()
    private val db get() = AppDatabase.getDatabase(context)
    private val store get() = PendingPaymentStore(RoomPendingPaymentBackend(db))

    @Before fun foregroundAppWithoutChangingPermissions() {
        assertTrue("POST_NOTIFICATIONS must already be granted; test will not grant it", manager.areNotificationsEnabled())
        // Instrumentation force-stops the target first. HyperOS may reject its first
        // self-start with result=102, even though a user/shell launch works. Establish
        // foreground through the test harness, not by changing production permissions.
        val launch = instrumentation.uiAutomation.executeShellCommand(
            "am start -W -n com.pixel.moneytracker/.MainActivity")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(launch).bufferedReader().use { it.readText() }
        assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)), 5000))
        assertTrue(waitUntil { runBlocking { db.ledgerDao().getById(1) != null } })
    }

    @After fun cleanupOnlyOwnCandidatesAndMarkedRecords() {
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<PaymentConfirmActivity>().forEach { it.finish() }
            ids.forEach { FloatingWindowService.detachCandidate(it) }
        }
        runBlocking {
            ids.forEach { id ->
                PendingPaymentNotifications(context).cancel(id)
                // Keep tombstones: a cached test PendingIntent must not become valid again.
                store.ignore(id)
            }
            db.recordDao().getAllRecords().first().filter { it.note.contains(marker) }.forEach {
                db.recordDao().deleteById(it.id)
            }
        }
    }

    private fun waitUntil(timeout: Long = 5000, predicate: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return true
            SystemClock.sleep(50)
        }
        return predicate()
    }

    private fun publish(amount: Double = 12.34, income: Boolean = false): Pair<PendingPayment, PendingIntent> {
        val candidate = runBlocking { store.create(amount, "支付宝", marker, income) }
        ids.add(candidate.id)
        assertTrue(PendingPaymentNotifications(context).post(candidate))
        assertTrue(waitUntil { manager.activeNotifications.any { it.tag == candidate.id } })
        val notification = manager.activeNotifications.single { it.tag == candidate.id }.notification
        assertEquals("支付宝账单待确认", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertNotNull(notification.publicVersion)
        assertFalse(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains(marker))
        return candidate to notification.contentIntent
    }

    private fun open(pendingIntent: PendingIntent, amount: String) {
        // Explicit simulation of the user's notification click; not a service trampoline.
        pendingIntent.send()
        assertTrue(device.wait(Until.hasObject(By.text(amount)), 5000))
        var ordinaryActivity = false
        instrumentation.runOnMainSync {
            ordinaryActivity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .any { it is PaymentConfirmActivity }
        }
        assertTrue("Click must open the normal confirmation Activity", ordinaryActivity)
        assertTrue("Wait for the Activity window to accept input, not only compose its amount", waitUntil {
            var focused = false
            instrumentation.runOnMainSync {
                focused = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<PaymentConfirmActivity>().any { it.hasWindowFocus() }
            }
            focused
        })
        instrumentation.waitForIdleSync()
        device.waitForIdle()
    }

    private fun hideKeyboard() {
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<PaymentConfirmActivity>().forEach { activity ->
                    androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                        .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
                }
        }
        device.waitForIdle()
    }

    private fun markedRecords() = runBlocking { db.recordDao().getAllRecords().first().filter { it.note.contains(marker) } }

    @Test fun cancelledNotificationRestoresExistingCandidateWithoutReplayingConsumedOnes() {
        val (candidate, _) = publish(78.91)
        val (consumed, _) = publish(79.91)
        runBlocking { store.ignore(consumed.id) }
        PendingPaymentNotifications(context).cancel(candidate.id)
        PendingPaymentNotifications(context).cancel(consumed.id)
        assertTrue(waitUntil { manager.activeNotifications.none { it.tag in setOf(candidate.id, consumed.id) } })
        runBlocking { PendingPayments.restoreNotifications(context) }
        assertTrue(waitUntil { manager.activeNotifications.any { it.tag == candidate.id } })
        assertFalse(manager.activeNotifications.any { it.tag == consumed.id })
        val click = manager.activeNotifications.single { it.tag == candidate.id }.notification.contentIntent
        open(click, "¥78.91")
        device.findObject(By.text("忽略")).click()
        assertTrue(waitUntil { manager.activeNotifications.none { it.tag == candidate.id } })
        runBlocking { PendingPayments.restoreNotifications(context) }
        assertFalse(manager.activeNotifications.any { it.tag in setOf(candidate.id, consumed.id) })
        assertTrue(markedRecords().isEmpty())
    }

    @Test fun notificationClickEditsAndConfirmsExactlyOnceThenOldIntentCannotReplay() {
        val (candidate, click) = publish(income = true)
        open(click, "¥12.34")
        assertTrue(device.hasObject(By.text("💰 检测到收入")))
        device.findObject(By.text("收入")).click() // Correct an income recognition to expense.
        device.findObject(By.text("餐饮")).click()
        val edit = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 3000)
        assertNotNull(edit)
        edit.text = marker + "_edited"
        hideKeyboard() // Do not let an absent IME turn Back into Activity cancellation.
        assertTrue(device.hasObject(By.text("记账 ✓")))
        device.findObject(By.text("记账 ✓")).click()
        assertTrue(waitUntil { markedRecords().size == 1 })
        assertTrue(waitUntil { manager.activeNotifications.none { it.tag == candidate.id } })
        val saved = markedRecords().single()
        assertEquals(12.34, saved.amount, 0.00001)
        assertEquals("EXPENSE", saved.type.name)
        assertEquals("餐饮", saved.category)
        assertTrue(saved.note.endsWith(marker + "_edited"))
        click.send()
        instrumentation.waitForIdleSync()
        assertTrue(waitUntil { runBlocking { store.load(candidate.id) == null } })
        SystemClock.sleep(500)
        assertFalse(device.hasObject(By.text("记账 ✓")))
        assertEquals(1, markedRecords().size)
    }

    @Test fun ignoreCreatesNoRecordAndRejectsOldIntent() {
        val (candidate, click) = publish(23.45)
        open(click, "¥23.45")
        device.findObject(By.text("忽略")).click()
        assertTrue(waitUntil { manager.activeNotifications.none { it.tag == candidate.id } })
        click.send()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(500)
        assertFalse(device.hasObject(By.text("记账 ✓")))
        assertTrue(markedRecords().isEmpty())
    }

    @Test fun backKeepsNotificationAndCandidateForLaterClick() {
        val (candidate, click) = publish(34.56)
        open(click, "¥34.56")
        device.pressBack()
        assertTrue(device.wait(Until.gone(By.text("记账 ✓")), 3000))
        assertNotNull(runBlocking { store.load(candidate.id) })
        assertTrue(manager.activeNotifications.any { it.tag == candidate.id })
        open(click, "¥34.56")
        device.findObject(By.text("忽略")).click()
        assertTrue(markedRecords().isEmpty())
    }

    @Test fun multipleSameAmountCandidatesHaveIndependentNotifications() {
        val (first, clickFirst) = publish(45.67)
        val (second, clickSecond) = publish(45.67)
        assertNotEquals(first.id, second.id)
        assertNotEquals(clickFirst, clickSecond)
        open(clickFirst, "¥45.67")
        val ignore = device.findObject(By.text("忽略"))
        val ignoreBounds = ignore.visibleBounds
        device.click(ignoreBounds.centerX(), ignoreBounds.centerY())
        val firstCleared = waitUntil { manager.activeNotifications.none { it.tag == first.id } }
        if (!firstCleared) {
            val states = runBlocking { listOf(db.pendingPaymentDao().get(first.id)?.status, db.pendingPaymentDao().get(second.id)?.status) }
            var shownId: String? = null
            instrumentation.runOnMainSync {
                shownId = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<PaymentConfirmActivity>().firstOrNull()?.intent?.data?.lastPathSegment
            }
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "pending-ignore-failure.png"))
            fail("Ignore tap did not clear first notification: states=$states shownFirst=${shownId == first.id} shownSecond=${shownId == second.id}")
        }
        assertNotNull(runBlocking { store.load(second.id) })
        assertTrue(manager.activeNotifications.any { it.tag == second.id })
        open(clickSecond, "¥45.67")
        device.findObject(By.text("记账 ✓")).click()
        assertTrue(waitUntil { markedRecords().size == 1 })
    }

    @Test fun activityRecreationRetainsCandidateAndEditedNote() {
        val (_, click) = publish(56.78)
        open(click, "¥56.78")
        device.wait(Until.findObject(By.clazz("android.widget.EditText")), 3000).text = marker + "_rotation"
        hideKeyboard()
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<PaymentConfirmActivity>().single().recreate()
        }
        assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText").text(marker + "_rotation")), 5000))
        device.findObject(By.text("记账 ✓")).click()
        assertTrue(waitUntil { markedRecords().size == 1 })
        assertTrue(markedRecords().single().note.endsWith(marker + "_rotation"))
    }

    @Test fun replacingOverlayAndDestroyingServiceDoesNotIgnoreEitherCandidate() {
        assertTrue(android.provider.Settings.canDrawOverlays(context))
        fun showAndFind(suffix: String, amount: Double): PendingPayment {
            context.startService(Intent(context, FloatingWindowService::class.java).apply {
                putExtra(FloatingWindowService.EXTRA_AMOUNT, amount)
                putExtra(FloatingWindowService.EXTRA_SOURCE, "支付宝")
                putExtra(FloatingWindowService.EXTRA_NOTE, marker + suffix)
            })
            var found: PendingPayment? = null
            assertTrue(waitUntil {
                found = manager.activeNotifications.mapNotNull { notification ->
                    notification.tag?.let { id -> runBlocking { store.load(id) } }
                }.firstOrNull { it.note == marker + suffix }
                found != null
            })
            ids.add(found!!.id)
            return found!!
        }
        val first = showAndFind("_first", 68.90)
        val second = showAndFind("_second", 69.90)
        instrumentation.runOnMainSync {
            assertFalse(FloatingWindowService.isShowingCandidate(first.id))
            assertTrue(FloatingWindowService.isShowingCandidate(second.id))
            FloatingWindowService.detachCandidate(first.id)
            assertTrue("Detaching a replaced candidate must not remove a different overlay", FloatingWindowService.isShowingCandidate(second.id))
        }
        context.stopService(Intent(context, FloatingWindowService::class.java))
        assertTrue(waitUntil {
            var active = true
            instrumentation.runOnMainSync { active = FloatingWindowService.isActive() }
            !active
        })
        assertNotNull(runBlocking { store.load(first.id) })
        assertNotNull(runBlocking { store.load(second.id) })
        val oldClick = manager.activeNotifications.single { it.tag == first.id }.notification.contentIntent
        open(oldClick, "¥68.90")
        device.findObject(By.text("忽略")).click()
        assertTrue(waitUntil { manager.activeNotifications.none { it.tag == first.id } })
        assertTrue(manager.activeNotifications.any { it.tag == second.id })
        assertTrue(markedRecords().isEmpty())
    }

    @Test fun serviceNotificationClickRemovesOnlyMatchingOverlayBeforeActivity() {
        assertTrue("This overlay-path test requires existing overlay permission", android.provider.Settings.canDrawOverlays(context))
        context.startService(Intent(context, FloatingWindowService::class.java).apply {
            putExtra(FloatingWindowService.EXTRA_AMOUNT, 67.89)
            putExtra(FloatingWindowService.EXTRA_SOURCE, "支付宝")
            putExtra(FloatingWindowService.EXTRA_NOTE, marker)
        })
        var candidate: PendingPayment? = null
        assertTrue(waitUntil {
            val own = manager.activeNotifications.firstOrNull {
                val id = it.tag ?: return@firstOrNull false
                val pending = runBlocking { store.load(id) }
                if (pending?.note == marker) { candidate = pending; true } else false
            }
            own != null
        })
        ids.add(candidate!!.id)
        val click = manager.activeNotifications.single { it.tag == candidate!!.id }.notification.contentIntent
        open(click, "¥67.89")
        instrumentation.runOnMainSync { assertFalse(FloatingWindowService.isShowingCandidate(candidate!!.id)) }
        device.findObject(By.text("记账 ✓")).click()
        assertTrue(waitUntil { markedRecords().size == 1 })
        assertTrue(waitUntil { manager.activeNotifications.none { it.tag == candidate!!.id } })
    }
}
