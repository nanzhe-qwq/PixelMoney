package com.pixel.moneytracker

import android.content.Intent
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.pixel.moneytracker.data.database.AppDatabase
import com.pixel.moneytracker.service.FloatingWindowService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Use an explicitly selected physical device via adb -s. No real payments or permission changes. */
@RunWith(AndroidJUnit4::class)
class AutoAccountingDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val marker = "AUTO_TEST_" + System.nanoTime()
    private val db get() = AppDatabase.getDatabase(context)

    @Before fun launch() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        device.wait(Until.hasObject(By.text("首页")), 5000)
    }

    @After fun cleanup() {
        context.stopService(Intent(context, FloatingWindowService::class.java))
        runBlocking {
            // Alipay parser replays now also create notifications. Clean only this test's candidates.
            val ownCandidates = mutableListOf<String>()
            db.openHelper.readableDatabase.query("SELECT id, note FROM pending_payments").use { cursor ->
                while (cursor.moveToNext()) if (cursor.getString(1).contains(marker)) ownCandidates.add(cursor.getString(0))
            }
            val pendingStore = com.pixel.moneytracker.service.PendingPayments.store(context)
            ownCandidates.forEach { id ->
                pendingStore.ignore(id)
                com.pixel.moneytracker.service.PendingPaymentNotifications(context).cancel(id)
            }
            db.recordDao().getAllRecords().first().filter { it.note.contains(marker) }.forEach {
                db.recordDao().deleteById(it.id)
            }
            assertTrue("Test records must be removed", db.recordDao().getAllRecords().first().none { it.note.contains(marker) })
        }
    }

    private fun show(amount: Double = 12.34) {
        context.startService(Intent(context, FloatingWindowService::class.java).apply {
            putExtra(FloatingWindowService.EXTRA_AMOUNT, amount)
            putExtra(FloatingWindowService.EXTRA_SOURCE, "测试样本")
            putExtra(FloatingWindowService.EXTRA_NOTE, marker)
        })
        assertTrue("Confirmation overlay should appear", device.wait(Until.hasObject(By.text("记账 ✓")), 5000))
    }

    @Test fun confirmationAppearsWhenAppIsInBackground() {
        assertTrue(device.pressHome())
        assertTrue("Launcher must be foreground before testing a background prompt",
            device.wait(Until.hasObject(By.pkg(device.launcherPackageName)), 5000))
        show()
        device.findObject(By.text("忽略")).click()
        assertTrue(device.wait(Until.gone(By.text("记账 ✓")), 3000))
    }

    @Test fun overlayNoteAcceptsKeyboardInput() {
        show()
        val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
        assertNotNull("Note field should exist", field)
        // 首次点击偶尔会落空（前台页面没切干净或 IME 还没接上），点几次再判定
        var focused = false
        repeat(4) {
            if (!focused) {
                field.click()
                focused = device.wait(Until.hasObject(By.clazz("android.widget.EditText").focused(true)), 1500)
            }
        }
        assertTrue("Note must receive focus before keyboard input", focused)
        val inputDeadline = android.os.SystemClock.uptimeMillis() + 3000
        var inputReady = false
        while (!inputReady && android.os.SystemClock.uptimeMillis() < inputDeadline) {
            instrumentation.runOnMainSync {
                inputReady = (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).isAcceptingText
            }
            if (!inputReady) android.os.SystemClock.sleep(50)
        }
        assertTrue("IME connection must be ready before injecting keys", inputReady)
        device.pressKeyCode(KeyEvent.KEYCODE_A)
        // A Chinese IME may keep the key in its composing buffer until Enter commits it.
        device.pressKeyCode(KeyEvent.KEYCODE_ENTER)
        val editedText = java.util.regex.Pattern.compile("(?s).{${marker.length + 1},}")
        assertTrue("Overlay note must accept committed keyboard input",
            device.wait(Until.hasObject(By.clazz("android.widget.EditText").text(editedText)), 3000))
        device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "overlay-keyboard.png"))
        device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null), "overlay-keyboard.xml"))
        device.pressBack()
    }

    private fun accessibilityParser(className: String = ""): (List<String>) -> Unit {
        val service = com.pixel.moneytracker.service.PaymentAccessibilityService()
        android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", android.content.Context::class.java).apply {
            isAccessible = true
            invoke(service, context)
        }
        val parse = service.javaClass.getDeclaredMethod("parseAndShow", String::class.java, List::class.java, String::class.java).apply { isAccessible = true }
        return { texts -> instrumentation.runOnMainSync { parse.invoke(service, "支付宝", texts, className) } }
    }

    @Test fun accessibilityIgnoresUnpaidDetail() {
        accessibilityParser()(listOf("账单详情", "待付款", "支付金额", "¥77.43"))
        assertFalse("Unpaid accessibility screen must not show a confirmation",
            device.wait(Until.hasObject(By.text("记账 ✓")), 1500))
    }

    @Test fun accessibilitySameAmountAtDifferentMerchantsIsNotSuppressed() {
        val parse = accessibilityParser()
        parse(listOf("账单详情", "支付成功", "实际支付", "¥77.44", "商户名称", marker + "_A"))
        assertTrue(device.wait(Until.hasObject(By.text("记账 ✓")), 3000))
        device.findObject(By.text("忽略")).click()
        assertTrue(device.wait(Until.gone(By.text("记账 ✓")), 3000))
        parse(listOf("账单详情", "支付成功", "实际支付", "¥77.44", "商户名称", marker + "_B"))
        assertTrue("Different merchant with the same amount must not be discarded",
            device.wait(Until.hasObject(By.text("记账 ✓")), 3000))
    }

    @Test fun accessibilityReceiptUsesActualAmountAndExpense() {
        accessibilityParser()(listOf("账单详情", "支付成功", "订单金额", "¥99.00", "实际支付", "¥77.45", "商户名称", marker))
        assertTrue(device.wait(Until.hasObject(By.text("¥77.45")), 3000))
        device.findObject(By.text("记账 ✓")).click()
        assertTrue(device.wait(Until.gone(By.text("记账 ✓")), 5000))
        val records = runBlocking { db.recordDao().getAllRecords().first().filter { it.note.contains(marker) } }
        assertEquals(1, records.size)
        assertEquals(77.45, records.single().amount, 0.00001)
        assertEquals("EXPENSE", records.single().type.name)
    }

    @Suppress("DEPRECATION") // Public SDK constructor for callback replay; no notification is posted.
    @Test fun styledIncomeNotificationReachesOverlayAndSavesAsIncome() {
        val service = com.pixel.moneytracker.service.PaymentNotificationService()
        android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", android.content.Context::class.java).apply {
            isAccessible = true
            invoke(service, context)
        }
        val notification = android.app.Notification.Builder(context, "sample-not-posted").build().apply {
            extras.putCharSequence(android.app.Notification.EXTRA_TITLE, android.text.SpannableString("收款成功"))
            extras.putCharSequence(android.app.Notification.EXTRA_TEXT, android.text.SpannableString("￥28.60 「$marker」"))
        }
        val sbn = android.service.notification.StatusBarNotification(
            "com.eg.android.AlipayGphone", "com.eg.android.AlipayGphone", 1, null,
            android.os.Process.myUid(), android.os.Process.myPid(), 0, notification,
            android.os.Process.myUserHandle(), System.currentTimeMillis())
        // Replay the real Android callback without posting or granting notification access.
        instrumentation.runOnMainSync { service.onNotificationPosted(sbn) }
        assertTrue("Styled notification should reach the income confirmation",
            device.wait(Until.hasObject(By.text("💰 检测到收入")), 3000))
        assertTrue(device.hasObject(By.text("¥28.60")))
        device.findObject(By.text("记账 ✓")).click()
        assertTrue(device.wait(Until.gone(By.text("记账 ✓")), 5000))
        val records = runBlocking { db.recordDao().getAllRecords().first().filter { it.note.contains(marker) } }
        assertEquals(1, records.size)
        assertEquals(28.60, records.single().amount, 0.00001)
        assertEquals("INCOME", records.single().type.name)
    }

    @Suppress("DEPRECATION")
    @Test fun repeatedNotificationMustNotResetEditedConfirmation() {
        val service = com.pixel.moneytracker.service.PaymentNotificationService()
        android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", android.content.Context::class.java).apply {
            isAccessible = true
            invoke(service, context)
        }
        val notification = android.app.Notification.Builder(context, "sample-not-posted").build().apply {
            extras.putCharSequence(android.app.Notification.EXTRA_TITLE, "付款成功")
            extras.putCharSequence(android.app.Notification.EXTRA_TEXT, "向$marker 付款12.50元")
        }
        val sbn = android.service.notification.StatusBarNotification(
            "com.eg.android.AlipayGphone", "com.eg.android.AlipayGphone", 7, null,
            android.os.Process.myUid(), android.os.Process.myPid(), 0, notification,
            android.os.Process.myUserHandle(), System.currentTimeMillis())
        instrumentation.runOnMainSync { service.onNotificationPosted(sbn) }
        val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 3000)
        assertNotNull(field)
        field.text = marker + "_edited"
        device.waitForIdle()
        assertEquals(marker + "_edited", device.findObject(By.clazz("android.widget.EditText")).text)
        instrumentation.runOnMainSync { service.onNotificationPosted(sbn) }
        instrumentation.waitForIdleSync()
        device.waitForIdle()
        assertEquals("A repeated notification must not recreate the confirmation and discard user edits",
            marker + "_edited", device.wait(Until.findObject(By.clazz("android.widget.EditText")), 3000)?.text)
    }

    @Suppress("DEPRECATION")
    @Test fun distinctSameAmountNotificationsAreNotDeduplicated() {
        val service = com.pixel.moneytracker.service.PaymentNotificationService()
        android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", android.content.Context::class.java).apply {
            isAccessible = true
            invoke(service, context)
        }
        val notification = android.app.Notification.Builder(context, "sample-not-posted").build().apply {
            extras.putCharSequence(android.app.Notification.EXTRA_TITLE, "付款成功")
            extras.putCharSequence(android.app.Notification.EXTRA_TEXT, "向$marker 付款12.50元")
        }
        val postedAt = System.currentTimeMillis()
        for ((id, time) in listOf(8 to postedAt, 8 to (postedAt + 1), 9 to postedAt)) {
            val sbn = android.service.notification.StatusBarNotification(
                "com.eg.android.AlipayGphone", "com.eg.android.AlipayGphone", id, null,
                android.os.Process.myUid(), android.os.Process.myPid(), 0, notification,
                android.os.Process.myUserHandle(), time)
            instrumentation.runOnMainSync { service.onNotificationPosted(sbn) }
            assertTrue("A distinct notification must remain eligible even with identical payment text",
                device.wait(Until.hasObject(By.text("记账 ✓")), 3000))
            device.findObject(By.text("忽略")).click()
            assertTrue(device.wait(Until.gone(By.text("记账 ✓")), 3000))
        }
    }

    @Test fun authenticationAndPendingScreensDoNotReachOverlayButCompletedReceiptDoes() {
        val receipt = listOf("账单详情", "支付成功", "实际支付", "¥78.40", "商户名称", marker)
        accessibilityParser("payment.FingerprintDialog")(receipt)
        assertFalse("An authentication overlay must not reuse an underlying receipt",
            device.wait(Until.hasObject(By.text("记账 ✓")), 1500))
        val parse = accessibilityParser("payment.BillDetailActivity")
        parse(receipt + "待入账")
        assertFalse("Pending credit must not open a completed-payment prompt",
            device.wait(Until.hasObject(By.text("记账 ✓")), 1500))
        parse(receipt)
        assertTrue("Rejecting transient states must not latch or suppress the later completed receipt",
            device.wait(Until.hasObject(By.text("¥78.40")), 3000))
    }

    @Test fun alipayResultUsesBareSettledAmountInsteadOfOrderPrice() {
        val before = runBlocking { db.recordDao().getAllRecords().first().map { it.id }.toSet() }
        accessibilityParser("com.alipay.android.msp.ui.views.MspContainerActivity")(
            listOf("支付成功￥18.81", marker, "￥20.00", "百次立减", "-￥1.19", "点击给商家对账", "完成"))
        assertTrue("Msp result must show settled amount, not the original price",
            device.wait(Until.hasObject(By.text("¥18.81")), 3000))
        assertFalse(device.hasObject(By.text("¥20.00")))
        device.findObject(By.text("忽略")).click()
        assertTrue(device.wait(Until.gone(By.text("记账 ✓")), 3000))
        assertEquals(before, runBlocking { db.recordDao().getAllRecords().first().map { it.id }.toSet() })
    }

    @Test fun alipayZeroSettledResultDoesNotFallBackToOrderPrice() {
        val before = runBlocking { db.recordDao().getAllRecords().first().map { it.id }.toSet() }
        accessibilityParser("com.alipay.android.msp.ui.views.MspContainerActivity")(
            listOf("支付成功￥0.00", marker, "￥0.01", "百次立减", "-￥0.01", "点击给商家对账", "完成"))
        assertFalse("Fully discounted result must not open an expense prompt for the order price",
            device.wait(Until.hasObject(By.text("记账 ✓")), 1500))
        assertEquals(before, runBlocking { db.recordDao().getAllRecords().first().map { it.id }.toSet() })
    }

    @Test fun ignoreDoesNotInsertRecord() {
        val before = runBlocking { db.recordDao().getAllRecords().first().map { it.id }.toSet() }
        show()
        device.findObject(By.text("忽略")).click()
        assertTrue(device.wait(Until.gone(By.text("记账 ✓")), 3000))
        val after = runBlocking { db.recordDao().getAllRecords().first().map { it.id }.toSet() }
        assertEquals(before, after)
    }

    @Test fun confirmInsertsExactlyOneRecord() {
        show()
        device.findObject(By.text("记账 ✓")).click()
        assertTrue(device.wait(Until.gone(By.text("记账 ✓")), 5000))
        val records = runBlocking { db.recordDao().getAllRecords().first().filter { it.note.contains(marker) } }
        assertEquals("One confirmation must insert one record", 1, records.size)
        assertEquals(12.34, records.single().amount, 0.00001)
        assertEquals("EXPENSE", records.single().type.name)
        assertTrue("Record must reference an existing ledger", runBlocking { db.ledgerDao().getById(records.single().ledgerId) != null })
    }
}
