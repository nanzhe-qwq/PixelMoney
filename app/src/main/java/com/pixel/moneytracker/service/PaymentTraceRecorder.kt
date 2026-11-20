package com.pixel.moneytracker.service

internal enum class TraceSource { ALIPAY_ACCESSIBILITY, WECHAT_ACCESSIBILITY, ALIPAY_NOTIFICATION, WECHAT_NOTIFICATION }
internal enum class TraceStage {
    RECEIVED, SNAPSHOT, NO_SCREEN, PARSE_MATCH, PARSE_REJECT, DUPLICATE,
    SERVICE_REQUEST, SERVICE_ACCEPTED, SERVICE_STARTED, VIEW_ATTACHED, FIRST_PRE_DRAW, ERROR,
    ROOT_SNAPSHOT, SOURCE_SNAPSHOT, ROOT_UNAVAILABLE, SOURCE_UNAVAILABLE,
    REJECT_TRANSIENT_PAGE, REJECT_BLOCKED_STATUS, REJECT_NO_COMPLETED_STATUS,
    REJECT_NO_RECEIPT_CONTEXT, REJECT_NO_AMOUNT, REJECT_ZERO_AMOUNT, REJECT_INVALID_AMOUNT
}
internal data class PaymentTrace(val id: Long, val source: TraceSource, val startedAt: Long)

/** Accept only enums and numbers: financial strings cannot enter the output schema. */
internal class PaymentTraceRecorder(
    private val enabled: Boolean,
    private val clock: () -> Long,
    private val sink: (String) -> Unit
) {
    private var nextId = 0L
    private var lines = 0
    private var sessionStart: Long? = null

    @Synchronized fun begin(source: TraceSource, eventAgeMs: Long = -1): PaymentTrace? {
        if (!enabled || lines >= 3000) return null
        val now = clock()
        val start = sessionStart ?: now.also { sessionStart = it }
        if (now - start > 900000L) return null
        val trace = PaymentTrace(++nextId, source, now)
        mark(trace, TraceStage.RECEIVED, eventAgeMs)
        return trace
    }

    fun markDecision(trace: PaymentTrace?, reason: ScreenParseReason) {
        if (reason == ScreenParseReason.MATCH) {
            mark(trace, TraceStage.PARSE_MATCH)
            return
        }
        mark(trace, TraceStage.PARSE_REJECT)
        val stage = when (reason) {
            ScreenParseReason.TRANSIENT_PAGE -> TraceStage.REJECT_TRANSIENT_PAGE
            ScreenParseReason.BLOCKED_STATUS -> TraceStage.REJECT_BLOCKED_STATUS
            ScreenParseReason.NO_COMPLETED_STATUS -> TraceStage.REJECT_NO_COMPLETED_STATUS
            ScreenParseReason.NO_RECEIPT_CONTEXT -> TraceStage.REJECT_NO_RECEIPT_CONTEXT
            ScreenParseReason.NO_AMOUNT -> TraceStage.REJECT_NO_AMOUNT
            ScreenParseReason.ZERO_AMOUNT -> TraceStage.REJECT_ZERO_AMOUNT
            ScreenParseReason.INVALID_AMOUNT -> TraceStage.REJECT_INVALID_AMOUNT
            ScreenParseReason.MATCH -> TraceStage.PARSE_MATCH
        }
        mark(trace, stage)
    }

    @Synchronized fun mark(trace: PaymentTrace?, stage: TraceStage, eventAgeMs: Long = -1, count: Int = -1) {
        if (!enabled || trace == null || lines >= 3000) return
        val now = clock()
        val start = sessionStart ?: now.also { sessionStart = it }
        if (now - start > 900000L) return
        lines++
        // Diagnostic output must never change payment control flow if logging fails.
        runCatching {
            sink("id=${trace.id} source=${trace.source.name} stage=${stage.name} uptime_ms=$now elapsed_ms=${(now - trace.startedAt).coerceAtLeast(0)} age_ms=${eventAgeMs.coerceAtLeast(-1)} count=${count.coerceAtLeast(-1)}")
        }
    }
}
