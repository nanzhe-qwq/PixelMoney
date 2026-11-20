package com.pixel.moneytracker

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.pixel.moneytracker.data.database.AppDatabase
import com.pixel.moneytracker.data.entity.Record
import com.pixel.moneytracker.service.FloatingWindowService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.rules.TestName
import org.junit.runner.Description
import org.junit.runner.RunWith

/** Uses only marked samples; existing user records must survive every test unchanged. */
@RunWith(AndroidJUnit4::class)
class UpgradeDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val db get() = AppDatabase.getDatabase(context)
    private val marker = "UPGRADE_TEST_${System.nanoTime()}"
    private var before = emptyList<Record>()
    @get:Rule val testName = TestName()
    @get:Rule val evidence = object : TestWatcher() {
        override fun failed(e: Throwable?, description: Description) {
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "upgrade-${description.methodName}.png"))
            device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null), "upgrade-${description.methodName}.xml"))
        }
    }

    @Before fun setup() {
        val launch = instrumentation.uiAutomation.executeShellCommand("am start -W -n com.pixel.moneytracker/.MainActivity")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(launch).bufferedReader().use { it.readText() }
        assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)), 5000))
        before = runBlocking { db.recordDao().getAllRecords().first() }
    }

    @After fun cleanup() {
        device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "before-cleanup-${testName.methodName}.png"))
        device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null), "before-cleanup-${testName.methodName}.xml"))
        context.stopService(Intent(context, FloatingWindowService::class.java))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS upgrade_test_save_failure")
        runBlocking {
            db.recordDao().getAllRecords().first().filter { it.note.contains(marker) }.forEach { db.recordDao().delete(it) }
            assertEquals("Existing bills must stay unchanged", before, db.recordDao().getAllRecords().first())
        }
    }

    /**
     * 备注输入框拿到焦点才写得进文字。支出/收入切换、IME 状态或上一个测试残留的前台页面
     * 都可能让首次点击落空，所以点几次并给每次留一点时间，比一次点击稳。
     */
    private fun focusNoteField(): androidx.test.uiautomator.UiObject2 {
        repeat(4) {
            val field = device.findObject(By.clazz("android.widget.EditText")) ?: return@repeat
            field.click()
            if (device.wait(Until.hasObject(By.clazz("android.widget.EditText").focused(true)), 1500)) {
                return field
            }
        }
        fail("备注输入框没有拿到焦点")
        error("unreachable")
    }

    private fun show(amount: Double, suffix: String = "", time: Long? = null) {
        context.startService(Intent(context, FloatingWindowService::class.java).apply {
            putExtra(FloatingWindowService.EXTRA_AMOUNT, amount)
            putExtra(FloatingWindowService.EXTRA_SOURCE, "测试样本")
            putExtra(FloatingWindowService.EXTRA_NOTE, marker + suffix)
            time?.let { putExtra(FloatingWindowService.EXTRA_TRANSACTION_TIME, it) }
        })
    }

    @Test fun distinctPaymentsQueueAndDuplicateDoesNotEraseEdits() {
        show(21.01, "A")
        assertTrue(device.wait(Until.hasObject(By.text("¥21.01")), 5000))
        val field = focusNoteField()
        field.text = marker + "EDITED"
        assertTrue(device.wait(Until.hasObject(By.text(marker + "EDITED")), 3000))
        show(21.01, "A")
        show(21.02, "B")
        instrumentation.waitForIdleSync()
        assertTrue(device.hasObject(By.text(marker + "EDITED")))
        assertFalse(device.hasObject(By.text("¥21.02")))
        device.pressBack()
        device.findObject(By.text("忽略")).click()
        assertTrue(device.wait(Until.hasObject(By.text("¥21.02")), 5000))
        device.findObject(By.text("忽略")).click()
        assertTrue(device.wait(Until.gone(By.text("记账 ✓")), 3000))
    }

    @Test fun failedSaveKeepsFormAndRetrySavesOnce() {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER upgrade_test_save_failure BEFORE INSERT ON records WHEN NEW.note LIKE '%$marker%' BEGIN SELECT RAISE(ABORT, 'test save failure'); END")
        show(21.03)
        assertTrue(device.wait(Until.hasObject(By.text("¥21.03")), 5000))
        device.findObject(By.text("记账 ✓")).click()
        android.os.SystemClock.sleep(700)
        assertTrue(device.hasObject(By.text("¥21.03")))
        assertTrue(runBlocking { db.recordDao().getAllRecords().first().none { it.note.contains(marker) } })
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER upgrade_test_save_failure")
        device.findObject(By.text("记账 ✓")).click()
        assertTrue(device.wait(Until.gone(By.text("记账 ✓")), 5000))
        assertEquals(1, runBlocking { db.recordDao().getAllRecords().first().count { it.note.contains(marker) } })
    }

    @Test fun historicalBillSavesOriginalDateAndCsvIncludesIt() {
        val time = java.time.LocalDateTime.of(2026, 10, 8, 12, 30).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        show(21.04, time = time)
        assertTrue(device.wait(Until.hasObject(By.text("2026-10-08 12:30")), 5000))
        device.waitForIdle()
        fun clickSave(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
            if (node.text?.toString() == "记账 ✓") {
                var target = node
                while (!target.isClickable && target.parent != null) target = target.parent
                return target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (clickSave(child)) return true
            }
            return false
        }
        assertTrue("Save must be accessible", instrumentation.uiAutomation.windows.any { it.root?.let(::clickSave) == true })
        assertTrue(device.wait(Until.gone(By.text("记账 ✓")), 5000))
        val record = runBlocking { db.recordDao().getAllRecords().first().single { it.note.contains(marker) } }
        assertEquals(2026, record.year)
        assertEquals(10, record.month)
        assertEquals(8, record.day)
        val csv = runBlocking { com.pixel.moneytracker.ui.viewmodel.MainViewModel(context.applicationContext as android.app.Application).exportRecordsCsv() }
        assertTrue(csv.contains("\"2026-10-08\""))
        assertTrue(csv.contains(marker))
    }

    @Test fun stalePopupCannotSaveOrDismissTheCurrentPayment() {
        show(21.05)
        assertTrue(device.wait(Until.hasObject(By.text("¥21.05")), 5000))
        val instanceField = FloatingWindowService::class.java.getDeclaredField("instance").apply { isAccessible = true }
        val service = instanceField.get(null) as FloatingWindowService
        val idField = FloatingWindowService::class.java.getDeclaredField("currentRequestId").apply { isAccessible = true }
        val id = idField.getLong(service)
        var closedStale = false
        instrumentation.runOnMainSync {
            FloatingWindowService.resolveActivity(id - 1,
                com.pixel.moneytracker.service.PaymentEdit("其他", "", marker, false),
                onSuccess = { closedStale = true }, onFailure = { fail("Stale requests must not write") })
        }
        assertTrue(closedStale)
        assertTrue(device.hasObject(By.text("¥21.05")))
        assertTrue(runBlocking { db.recordDao().getAllRecords().first().none { it.note.contains(marker) } })
        device.findObject(By.text("忽略")).click()
        assertTrue(device.wait(Until.gone(By.text("记账 ✓")), 3000))
    }

    @Test fun repeatedPaymentRestoresBackgroundDialogWithoutReplacingIt() {
        show(21.06)
        assertTrue(device.wait(Until.hasObject(By.text("¥21.06")), 5000))
        val instanceField = FloatingWindowService::class.java.getDeclaredField("instance").apply { isAccessible = true }
        val service = instanceField.get(null) as FloatingWindowService
        val idField = FloatingWindowService::class.java.getDeclaredField("currentRequestId").apply { isAccessible = true }
        val id = idField.getLong(service)
        device.pressHome()
        assertTrue(device.wait(Until.gone(By.text("¥21.06")), 3000))
        show(21.06)
        assertTrue(device.wait(Until.hasObject(By.text("¥21.06")), 5000))
        assertEquals(id, idField.getLong(service))
        device.findObject(By.text("忽略")).click()
        assertTrue(device.wait(Until.gone(By.text("记账 ✓")), 3000))
    }
}
