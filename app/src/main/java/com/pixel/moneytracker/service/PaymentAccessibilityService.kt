package com.pixel.moneytracker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 支付详情读取无障碍服务
 * 当用户在支付宝/微信中打开账单详情时，自动读取金额和商户信息，弹窗记账
 */
class PaymentAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PayAccess"
        private const val COOLDOWN_MS = 5000L
        private var lastDetectedTime = 0L
        private var lastDetectedKey = ""
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return


        if (!PaymentAppSettings.enabled(this) || !PaymentAppSettings.enabled(this, packageName)) return
        val source = PaymentAppSettings.screenApps[packageName] ?: return
        readPaymentScreen(source, event)
    }

    /** Re-read on every update: an early loading event must not latch an incomplete page. */
    @Suppress("DEPRECATION")
    private fun readPaymentScreen(source: String, event: AccessibilityEvent) {
        val traceSource = when (source) {
            "支付宝" -> TraceSource.ALIPAY_ACCESSIBILITY
            "微信" -> TraceSource.WECHAT_ACCESSIBILITY
            else -> null
        }
        val age = (android.os.SystemClock.uptimeMillis() - event.eventTime).takeIf { event.eventTime > 0 && it >= 0 } ?: -1
        val trace = traceSource?.let { PaymentTiming.begin(it, age) }
        val root = rootInActiveWindow
        val rootTexts = try {
            if (root?.packageName?.toString() == event.packageName?.toString()) getAllText(root)
            else emptyList()
        } finally {
            root?.recycle()
        }
        PaymentTiming.mark(trace,
            if (rootTexts.isNotEmpty()) TraceStage.ROOT_SNAPSHOT else TraceStage.ROOT_UNAVAILABLE,
            rootTexts.size)
        val texts = if (rootTexts.isNotEmpty()) rootTexts else {
            // WeChat may expose only an event subtree. Its ancestors contain the full receipt.
            var node = event.source ?: run {
                PaymentTiming.mark(trace, TraceStage.SOURCE_UNAVAILABLE)
                PaymentTiming.mark(trace, TraceStage.NO_SCREEN)
                return
            }
            try {
                var depth = 0
                while (depth++ < 32) {
                    val parent = node.parent ?: break
                    node.recycle()
                    node = parent
                }
                if (node.packageName?.toString() != event.packageName?.toString()) {
                    PaymentTiming.mark(trace, TraceStage.SOURCE_UNAVAILABLE)
                    PaymentTiming.mark(trace, TraceStage.NO_SCREEN)
                    return
                }
                getAllText(node).also {
                    PaymentTiming.mark(trace, TraceStage.SOURCE_SNAPSHOT, it.size)
                }
            } finally {
                node.recycle()
            }
        }
        PaymentTiming.mark(trace, TraceStage.SNAPSHOT, texts.size)
        parseAndShow(source, texts, event.className?.toString().orEmpty(), trace)
    }

    /**
     * 解析页面文本并弹窗
     */
    private fun parseAndShow(source: String, texts: List<String>, className: String) {
        parseAndShow(source, texts, className, null)
    }

    private fun parseAndShow(source: String, texts: List<String>, className: String, trace: PaymentTrace?) {
        val result = ScreenPaymentParser.inspect(source, className, texts)
        PaymentTiming.markDecision(trace, result.reason)
        val payment = result.payment ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        val amountStr = String.format(java.util.Locale.ROOT, "%.2f", payment.amount)
        val key = listOf(source, amountStr, payment.merchant, payment.isIncome.toString(), payment.transactionTime?.toString().orEmpty()).joinToString("|")
        if (key == lastDetectedKey && now - lastDetectedTime < COOLDOWN_MS) {
            PaymentTiming.mark(trace, TraceStage.DUPLICATE)
            return
        }
        lastDetectedTime = now
        lastDetectedKey = key
        val sourceLabel = if (payment.isIncome) "${payment.source}(收入)" else payment.source
        showFloatingWindow(payment.amount, sourceLabel, payment.merchant, payment.isIncome, trace, payment.transactionTime)
    }

    /**
     * 获取节点树中所有文本
     */
    // recycle() 在 API 33+ 已废弃且为空实现；为兼容旧版本保留，显式压制告警
    @Suppress("DEPRECATION")
    private fun getAllText(node: AccessibilityNodeInfo?): List<String> {
        if (node == null) return emptyList()
        val texts = mutableListOf<String>()

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && text != "null") {
            texts.add(text)
        }
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrEmpty() && desc != "null" && desc != text) {
            texts.add(desc)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            texts.addAll(getAllText(child))
            child.recycle()
        }

        return texts
    }

    /**
     * 启动悬浮窗
     */
    private fun showFloatingWindow(amount: Double, source: String, note: String, isIncome: Boolean = false, trace: PaymentTrace? = null, transactionTime: Long? = null) {
        val intent = Intent(this, FloatingWindowService::class.java).apply {
            putExtra(FloatingWindowService.EXTRA_AMOUNT, amount)
            putExtra(FloatingWindowService.EXTRA_SOURCE, source)
            putExtra(FloatingWindowService.EXTRA_TITLE, "账单详情")
            putExtra(FloatingWindowService.EXTRA_CONTENT, "")
            putExtra(FloatingWindowService.EXTRA_NOTE, note)
            putExtra(FloatingWindowService.EXTRA_IS_INCOME, isIncome)
            transactionTime?.let { putExtra(FloatingWindowService.EXTRA_TRANSACTION_TIME, it) }
            PaymentTiming.attach(this, trace)
        }
        PaymentTiming.mark(trace, TraceStage.SERVICE_REQUEST)
        try {
            val accepted = startService(intent) != null
            PaymentTiming.mark(trace, if (accepted) TraceStage.SERVICE_ACCEPTED else TraceStage.ERROR)
        } catch (e: Exception) {
            PaymentTiming.mark(trace, TraceStage.ERROR)
            Log.e(TAG, "启动悬浮窗失败: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "无障碍服务已销毁")
    }
}
