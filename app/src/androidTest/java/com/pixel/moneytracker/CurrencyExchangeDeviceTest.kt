package com.pixel.moneytracker

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.pixel.moneytracker.data.database.AppDatabase
import com.pixel.moneytracker.util.CurrencyExchange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurrencyExchangeDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation = instrumentation.uiAutomation
    private val device = UiDevice.getInstance(instrumentation)

    @Test fun androidParsesPublicEcbEnvelope() {
        val sample = """<gesmes:Envelope xmlns:gesmes="http://www.gesmes.org/xml/2002-08-01"
            xmlns="http://www.ecb.int/vocabulary/2002-08-01/eurofxref">
            <Cube><Cube time="2026-04-17"><Cube currency="USD" rate="1.20"/>
            <Cube currency="CNY" rate="7.92"/></Cube></Cube></gesmes:Envelope>"""
        val result = CurrencyExchange.parseQuote(sample, "USD")
        assertEquals("6.6", result.rate)
        assertEquals("2026-04-17", result.date.toString())
    }

    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null || node.packageName?.toString() != context.packageName) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) find(node.getChild(i), predicate)?.let { return it }
        return null
    }

    private fun clickLabel(label: String) {
        assertTrue(label, device.wait(Until.hasObject(By.text(label)), 5000) ||
            device.wait(Until.hasObject(By.desc(label)), 1000))
        var node = find(automation.rootInActiveWindow) {
            it.text?.toString() == label || it.contentDescription?.toString() == label
        }
        while (node != null && !node.isClickable) node = node.parent
        assertTrue(label, node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
    }

    private fun enter(index: Int, value: String) {
        var seen = 0
        val edit = find(automation.rootInActiveWindow) { it.isEditable && seen++ == index }
        assertNotNull("Expected editable field $index", edit)
        assertTrue(edit!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
        assertTrue(device.wait(Until.hasObject(By.text(value)), 3000))
    }

    @Test fun manualUsdRateFillsDraftAndClosingLeavesBillsUnchanged() {
        val prefs = context.getSharedPreferences("currency_rates", Context.MODE_PRIVATE)
        val prior = listOf("currency", "rate_USD", "ecb_date_USD").associateWith { prefs.getString(it, null) }
        val guide = context.getSharedPreferences("pixel_money", Context.MODE_PRIVATE)
        val hadGuide = guide.contains("has_seen_guide")
        val oldGuide = guide.getBoolean("has_seen_guide", false)
        try {
            prefs.edit().putString("currency", "USD").remove("rate_USD").remove("ecb_date_USD").commit()
            guide.edit().putBoolean("has_seen_guide", true).commit()
            val start = automation.executeShellCommand("am start -W --activity-clear-top -n com.pixel.moneytracker/.MainActivity")
            ParcelFileDescriptor.AutoCloseInputStream(start).use { it.readBytes() }
            val dao = AppDatabase.getDatabase(context).recordDao()
            val before = runBlocking { dao.getAllRecords().first() }
            clickLabel("记一笔")
            clickLabel("汇率换算")
            assertTrue(device.wait(Until.hasObject(By.text("填入账单")), 3000))
            enter(0, "10")
            enter(1, "7.25")
            assertTrue(device.wait(Until.hasObject(By.text("折合人民币 ¥72.50")), 3000))
            clickLabel("填入账单")
            assertTrue(device.wait(Until.hasObject(By.text("72.50")), 3000))
            assertTrue(device.hasObject(By.textContains("10 USD × 7.25")))
            clickLabel("关闭")
            assertTrue(device.wait(Until.hasObject(By.desc("记一笔")), 5000))
            assertEquals(before, runBlocking { dao.getAllRecords().first() })
        } finally {
            prefs.edit().also { editor -> prior.forEach { (key, value) -> editor.putString(key, value) } }.commit()
            guide.edit().also { if (hadGuide) it.putBoolean("has_seen_guide", oldGuide) else it.remove("has_seen_guide") }.commit()
        }
    }
}
