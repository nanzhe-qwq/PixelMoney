package com.pixel.moneytracker.reminder

import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderTimeTest {
    @Test fun schedulesNextLocalOccurrenceAcrossMidnightAndMonthEnd() {
        val before = ZonedDateTime.parse("2026-01-31T20:59:59+08:00[Asia/Shanghai]")
        val due = before.plusSeconds(1)
        assertEquals(due, nextReminder(before, 21 * 60))
        assertEquals(due.plusDays(1), nextReminder(due, 21 * 60))
    }

    @Test fun springClockChangeDoesNotPermanentlyShiftTheChosenHour() {
        val beforeGap = ZonedDateTime.parse("2026-03-07T23:00:00-05:00[America/New_York]")
        val first = nextReminder(beforeGap, 2 * 60 + 30)
        assertEquals(ZonedDateTime.parse("2026-03-08T03:30:00-04:00[America/New_York]"), first)
        assertEquals(ZonedDateTime.parse("2026-03-09T02:30:00-04:00[America/New_York]"),
            nextReminder(first, 2 * 60 + 30))
    }

    @Test fun repeatedHourUsesOnlyTheFirstOccurrenceThatDay() {
        val first = ZonedDateTime.parse("2026-11-01T01:30:00-04:00[America/New_York]")
        assertEquals(first, nextReminder(first.minusSeconds(1), 90))
        assertEquals(ZonedDateTime.parse("2026-11-02T01:30:00-05:00[America/New_York]"), nextReminder(first, 90))
    }
}
