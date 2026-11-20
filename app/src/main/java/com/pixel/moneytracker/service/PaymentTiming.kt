package com.pixel.moneytracker.service

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.pixel.moneytracker.BuildConfig

/** Build-time opt-in; never accepts notification/receipt strings or transaction identifiers. */
internal object PaymentTiming {
    private const val ID = "pixel_trace_id"
    private const val SOURCE = "pixel_trace_source"
    private const val START = "pixel_trace_start"
    private val recorder = PaymentTraceRecorder(
        BuildConfig.PAYMENT_TIMING_DIAGNOSTICS,
        SystemClock::uptimeMillis,
        { Log.i("PayTiming", it) }
    )

    fun begin(source: TraceSource, eventAgeMs: Long = -1): PaymentTrace? = recorder.begin(source, eventAgeMs)
    fun mark(trace: PaymentTrace?, stage: TraceStage, count: Int = -1) = recorder.mark(trace, stage, count = count)
    fun markDecision(trace: PaymentTrace?, reason: ScreenParseReason) = recorder.markDecision(trace, reason)

    fun attach(intent: Intent, trace: PaymentTrace?) {
        if (trace == null) return
        intent.putExtra(ID, trace.id)
        intent.putExtra(SOURCE, trace.source.name)
        intent.putExtra(START, trace.startedAt)
    }

    fun restore(intent: Intent?): PaymentTrace? {
        if (!BuildConfig.PAYMENT_TIMING_DIAGNOSTICS || intent == null) return null
        val id = intent.getLongExtra(ID, -1)
        val start = intent.getLongExtra(START, -1)
        val source = intent.getStringExtra(SOURCE)?.let { name -> TraceSource.entries.firstOrNull { it.name == name } }
        return if (id > 0 && start >= 0 && source != null) PaymentTrace(id, source, start) else null
    }
}
