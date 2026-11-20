package com.pixel.moneytracker.util

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringScheduleTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Long = ZonedDateTime
        .parse("2026-11-20T10:00:00+08:00[Asia/Shanghai]")
        .toInstant()
        .toEpochMilli()

    private fun daysAgo(days: Long): Long = LocalDateTime
        .parse("2026-11-20T10:00:00")
        .atZone(zone)
        .minusDays(days)
        .toInstant()
        .toEpochMilli()

    @Test
    fun brandNewRuleExecutesImmediatelyLikeBefore() {
        val result = RecurringSchedule.occurrences(0L, 30, now, zone)
        assertEquals(1, result.size)
        assertEquals(now, result.single().at)
        assertTrue(result.single().withinWindow)
        assertEquals("2026-11-20", result.single().date.toString())
    }

    @Test
    fun clockRolledBackDoesNothing() {
        assertTrue(RecurringSchedule.occurrences(now + 86_400_000L, 1, now, zone).isEmpty())
    }

    @Test
    fun missedDailyRunsAreCaughtUpInOrder() {
        val result = RecurringSchedule.occurrences(daysAgo(3), 1, now, zone)
        assertEquals(3, result.size)
        assertEquals(listOf("2026-11-18", "2026-11-19", "2026-11-20"), result.map { it.date.toString() })
        assertTrue(result.all { it.withinWindow })
    }

    @Test
    fun longGapIsCappedAndOldOccurrencesAreOutsideWindow() {
        val result = RecurringSchedule.occurrences(daysAgo(100), 1, now, zone)
        assertEquals(RecurringSchedule.MAX_CATCH_UP_OCCURRENCES, result.size)
        // 只推进 12 天，剩下的留给下一次调度继续追，不会一次性灌满账本
        assertEquals("2026-08-13", result.first().date.toString())
        assertFalse(result.any { it.withinWindow })
    }

    @Test
    fun sixtyTwoDayWindowBoundaryIsInclusive() {
        val result = RecurringSchedule.occurrences(daysAgo(70), 1, now, zone)
        assertEquals(12, result.size)
        val inWindow = result.filter { it.withinWindow }
        assertEquals(5, inWindow.size)
        assertEquals("2026-09-19", inWindow.first().date.toString())
    }

    @Test
    fun nextCheckSkipsPastOccurrencesButKeepsMinimumDelay() {
        assertEquals(now + 60_000L, RecurringSchedule.nextCheckAt(0L, 1, now))
        // 到期点已经过去时不能立刻回调，否则会空转
        assertEquals(now + 60_000L, RecurringSchedule.nextCheckAt(daysAgo(1), 1, now))
        assertEquals(now + 7L * 86_400_000L, RecurringSchedule.nextCheckAt(now, 7, now))
    }
}
