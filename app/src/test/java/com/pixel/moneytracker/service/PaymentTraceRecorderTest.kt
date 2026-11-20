package com.pixel.moneytracker.service

import org.junit.Assert.*
import org.junit.Test

class PaymentTraceRecorderTest {
    @Test fun parseDecisionsLogOnlyTheirClosedReasonAndNeverPaymentDetails() {
        for (reason in ScreenParseReason.entries) {
            val logs = mutableListOf<String>()
            val recorder = PaymentTraceRecorder(true, { 1000L }, { logs.add(it) })
            val trace = recorder.begin(TraceSource.ALIPAY_ACCESSIBILITY)!!
            recorder.markDecision(trace, reason)
            if (reason == ScreenParseReason.MATCH) {
                assertEquals(2, logs.size)
                assertTrue(logs.last().contains("stage=PARSE_MATCH "))
            } else {
                assertEquals(3, logs.size)
                assertTrue(logs[1].contains("stage=PARSE_REJECT "))
                assertTrue(logs.last().contains("stage=REJECT_${reason.name} "))
            }
            assertTrue(logs.all { line -> line.split(' ').all { token ->
                token.substringBefore('=') in setOf("id", "source", "stage", "uptime_ms", "elapsed_ms", "age_ms", "count")
            } })
        }
    }

    @Test fun outputIsBoundedEvenWhenAccessibilityEventsAreContinuous() {
        var lines = 0
        val recorder = PaymentTraceRecorder(true, { 1000L }, { lines++ })
        repeat(4000) { recorder.begin(TraceSource.ALIPAY_ACCESSIBILITY) }
        assertEquals(3000, lines)
    }

    @Test fun diagnosticsExpireWithoutChangingTheClockOrRecordingMore() {
        var now = 1000L
        val logs = mutableListOf<String>()
        val recorder = PaymentTraceRecorder(true, { now }, { logs.add(it) })
        assertNotNull(recorder.begin(TraceSource.ALIPAY_NOTIFICATION))
        now += 900001L
        assertNull(recorder.begin(TraceSource.ALIPAY_NOTIFICATION))
        assertEquals(1, logs.size)
    }

    @Test fun disabledDiagnosticsDoNotReadClockOrCallSink() {
        val recorder = PaymentTraceRecorder(false, { error("disabled clock") }, { error("disabled sink") })
        assertNull(recorder.begin(TraceSource.WECHAT_NOTIFICATION))
        recorder.mark(null, TraceStage.ERROR)
    }

    @Test fun diagnosticSinkFailureCannotBreakPaymentHandling() {
        val recorder = PaymentTraceRecorder(true, { 1000L }, { error("broken diagnostic sink") })
        val trace = recorder.begin(TraceSource.WECHAT_ACCESSIBILITY)
        assertNotNull(trace)
        recorder.mark(trace, TraceStage.FIRST_PRE_DRAW)
    }

    @Test fun timelineUsesMonotonicStageTimesAndAClosedMetadataSchema() {
        var now = 1000L
        val logs = mutableListOf<String>()
        val recorder = PaymentTraceRecorder(true, { now }, { logs.add(it) })
        val trace = recorder.begin(TraceSource.ALIPAY_ACCESSIBILITY, eventAgeMs = 250)!!
        now += 30
        recorder.mark(trace, TraceStage.SNAPSHOT, count = 42)
        now += 12
        recorder.mark(trace, TraceStage.PARSE_MATCH)
        assertEquals(3, logs.size)
        assertTrue(logs[0].contains("source=ALIPAY_ACCESSIBILITY stage=RECEIVED"))
        assertTrue(logs[0].contains("age_ms=250"))
        assertTrue(logs[1].contains("elapsed_ms=30"))
        assertTrue(logs[1].contains("count=42"))
        assertTrue(logs[2].contains("elapsed_ms=42"))
        val allowed = setOf("id", "source", "stage", "uptime_ms", "elapsed_ms", "age_ms", "count")
        for (line in logs) {
            assertEquals(allowed, line.split(' ').map { it.substringBefore('=') }.toSet())
            assertFalse(line.contains("amount"))
            assertFalse(line.contains("merchant"))
            assertFalse(line.contains("note"))
            assertFalse(line.contains("content"))
        }
    }
}
