package com.pixel.moneytracker

import android.content.Context
import android.content.Intent
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.pixel.moneytracker.service.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Diagnostic-build-only integration; never saves a financial record or changes a permission. */
@RunWith(AndroidJUnit4::class)
class PaymentTimingDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private var ownsOverlay = false

    @Before fun requireDiagnosticBuildAndOverlayPermission() {
        val selectedClasses = InstrumentationRegistry.getArguments().getString("class")
        val explicitlySelected = selectedClasses?.split(',')?.any {
            it.trim().substringBefore('#') == javaClass.name
        } == true
        org.junit.Assume.assumeTrue("Diagnostic build and explicit test-class selection required",
            BuildConfig.PAYMENT_TIMING_DIAGNOSTICS && explicitlySelected)
        assertTrue("User must grant overlay access", android.provider.Settings.canDrawOverlays(context))
    }

    @After fun cleanup() {
        if (ownsOverlay) context.stopService(Intent(context, FloatingWindowService::class.java))
    }

    @Test fun intentTraceCarriesOnlyClosedMetadataAndRejectsMalformedSource() {
        val trace = PaymentTiming.begin(TraceSource.ALIPAY_ACCESSIBILITY)!!
        val intent = Intent()
        PaymentTiming.attach(intent, trace)
        assertEquals(setOf("pixel_trace_id", "pixel_trace_source", "pixel_trace_start"), intent.extras!!.keySet())
        assertEquals(trace, PaymentTiming.restore(intent))
        intent.putExtra("pixel_trace_source", "private text is not an allowed enum")
        assertNull(PaymentTiming.restore(intent))
        assertNull(PaymentTiming.restore(Intent()))
    }

    @Test fun zeroSettledReceiptIsRejectedWithoutOpeningAnOverlay() {
        val service = PaymentAccessibilityService()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply {
            isAccessible = true
            invoke(service, context)
        }
        val method = service.javaClass.getDeclaredMethod("parseAndShow", String::class.java,
            List::class.java, String::class.java, PaymentTrace::class.java).apply { isAccessible = true }
        val trace = PaymentTiming.begin(TraceSource.ALIPAY_ACCESSIBILITY)!!
        instrumentation.runOnMainSync {
            method.invoke(service, "支付宝",
                listOf("支付成功￥0.00", "DIAG_SAMPLE_ONLY", "￥0.01", "百次立减", "-￥0.01", "点击给商家对账", "完成"),
                "com.alipay.android.msp.ui.views.MspContainerActivity", trace)
        }
        assertFalse(device.wait(Until.hasObject(By.text("记账 ✓")), 1500))
    }

    @Test fun screenTraceReachesRealConfirmationWithoutSaving() {
        // Exercise the real overlay from launcher; Activity startup is not this test's seam.
        assertTrue(device.pressHome())
        assertTrue(device.wait(Until.hasObject(By.pkg(device.launcherPackageName)), 5000))
        val service = PaymentAccessibilityService()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply {
            isAccessible = true
            invoke(service, context)
        }
        val method = service.javaClass.getDeclaredMethod("parseAndShow", String::class.java,
            List::class.java, String::class.java, PaymentTrace::class.java).apply { isAccessible = true }
        val trace = PaymentTiming.begin(TraceSource.ALIPAY_ACCESSIBILITY)!!
        ownsOverlay = true
        instrumentation.runOnMainSync {
            method.invoke(service, "支付宝", listOf("支付成功", "实付金额", "¥19.27", "商户名称", "DIAG_SAMPLE_ONLY"), "BillDetail", trace)
        }
        assertTrue(device.wait(Until.hasObject(By.text("¥19.27")), 5000))
        device.findObject(By.text("忽略")).click()
        assertTrue(device.wait(Until.gone(By.text("记账 ✓")), 3000))
    }
}
