package com.pixel.moneytracker.service

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * 支付通知监听服务
 * 监听支付宝、微信、美团、饿了么、淘宝、京东、滴滴等多种支付场景
 */
class PaymentNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "PaymentNotify"

        // 回调接口
        var listener: PaymentListener? = null
    }

    interface PaymentListener {
        fun onPaymentDetected(amount: Double, source: String, title: String, content: String, note: String)
    }

    // Exact notification identity, not an amount/time heuristic: two real payments may look alike.
    private val handledNotifications = LinkedHashMap<Pair<String, Long>, PaymentNotificationParser.ParsedPayment>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val packageName = sbn.packageName ?: return
        if (!PaymentAppSettings.enabled(this) || !PaymentAppSettings.enabled(this, packageName)) return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val traceSource = when (packageName) {
            "com.eg.android.AlipayGphone" -> TraceSource.ALIPAY_NOTIFICATION
            "com.tencent.mm" -> TraceSource.WECHAT_NOTIFICATION
            else -> null
        }
        // Wall-clock notification age is metadata only, not payment completion latency.
        val age = (System.currentTimeMillis() - sbn.postTime).takeIf { sbn.postTime > 0 && it >= 0 } ?: -1
        val trace = traceSource?.let { PaymentTiming.begin(it, age) }
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val content = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        val payment = PaymentNotificationParser.parse(packageName, title, content, bigText, subText)
        PaymentTiming.mark(trace, if (payment == null) TraceStage.PARSE_REJECT else TraceStage.PARSE_MATCH)
        payment ?: return
        val identity = sbn.key to sbn.postTime
        if (handledNotifications[identity] == payment) {
            PaymentTiming.mark(trace, TraceStage.DUPLICATE)
            return
        }
        if (showFloatingWindow(payment.amount, payment.source, payment.title, payment.content, payment.note, payment.isIncome, trace)) {
            handledNotifications[identity] = payment
            while (handledNotifications.size > 128) {
                handledNotifications.remove(handledNotifications.keys.first())
            }
        }
    }

    /**
     * 启动悬浮窗确认弹窗
     */
    private fun showFloatingWindow(amount: Double, source: String, title: String, content: String, note: String, isIncome: Boolean = false, trace: PaymentTrace? = null): Boolean {
        listener?.onPaymentDetected(amount, source, title, content, note)

        val intent = Intent(this, FloatingWindowService::class.java).apply {
            putExtra(FloatingWindowService.EXTRA_AMOUNT, amount)
            putExtra(FloatingWindowService.EXTRA_SOURCE, source)
            putExtra(FloatingWindowService.EXTRA_TITLE, title)
            putExtra(FloatingWindowService.EXTRA_CONTENT, content)
            putExtra(FloatingWindowService.EXTRA_NOTE, note)
            putExtra(FloatingWindowService.EXTRA_IS_INCOME, isIncome)
            PaymentTiming.attach(this, trace)
        }
        PaymentTiming.mark(trace, TraceStage.SERVICE_REQUEST)
        return try {
            val accepted = startService(intent) != null
            PaymentTiming.mark(trace, if (accepted) TraceStage.SERVICE_ACCEPTED else TraceStage.ERROR)
            accepted
        } catch (e: Exception) {
            PaymentTiming.mark(trace, TraceStage.ERROR)
            Log.e(TAG, "启动悬浮窗失败: ${e.message}")
            false
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // 通知移除时不做处理
    }
}
