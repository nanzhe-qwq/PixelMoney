package com.pixel.moneytracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.view.doOnPreDraw
import com.pixel.moneytracker.service.*
import com.pixel.moneytracker.ui.theme.PixelMoneyTheme
import java.lang.ref.WeakReference

/** A translucent confirmation above the payment app, with no entry in recent tasks. */
class PaymentPopupActivity : ComponentActivity() {
    companion object {
        const val EXTRA_REQUEST_ID = "payment_request_id"
        private var current: WeakReference<PaymentPopupActivity>? = null
        fun close() { current?.get()?.finish() }
        fun isVisible() = current?.get()?.window?.decorView?.hasWindowFocus() == true
    }

    private var requestId by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = WeakReference(this)
        requestId = intent.getLongExtra(EXTRA_REQUEST_ID, 0L)
        val trace = PaymentTiming.restore(intent)
        window.decorView.doOnPreDraw { PaymentTiming.mark(trace, TraceStage.FIRST_PRE_DRAW) }
        setContent {
            key(requestId) {
                val payment = FloatingWindowService.activityPayment(requestId)
                if (payment == null) {
                    LaunchedEffect(requestId) { finish() }
                } else {
                    var busy by remember { mutableStateOf(false) }
                    fun resolve(edit: PaymentEdit?) {
                        if (busy) return
                        busy = true
                        FloatingWindowService.resolveActivity(requestId, edit,
                            onSuccess = { finish() }, onFailure = { busy = false })
                    }
                    BackHandler { if (!busy) resolve(null) }
                    PixelMoneyTheme {
                        PaymentConfirmDialog(
                            amount = payment.amount, source = payment.source,
                            initialNote = payment.merchant, isIncome = payment.isIncome,
                            transactionTime = payment.transactionTime, enabled = !busy,
                            onConfirm = { category, emoji, note, income -> resolve(PaymentEdit(category, emoji, note, income)) },
                            onDismiss = { resolve(null) }
                        )
                    }
                }
            }
        }
        window.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT)
        PaymentTiming.mark(trace, TraceStage.VIEW_ATTACHED)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestId = intent.getLongExtra(EXTRA_REQUEST_ID, 0L)
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }
}
