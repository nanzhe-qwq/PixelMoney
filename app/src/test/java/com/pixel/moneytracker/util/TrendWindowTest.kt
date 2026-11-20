package com.pixel.moneytracker.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TrendWindowTest {

    @Test
    fun lastMonthsEndsAtRequestedMonth() {
        val slots = TrendWindow.lastMonths(2026, 11, 6)
        assertEquals(listOf(6, 7, 8, 9, 10, 11), slots.map { it.month })
        assertEquals(listOf(2026, 2026, 2026, 2026, 2026, 2026), slots.map { it.year })
    }

    @Test
    fun januaryWalksBackIntoPreviousYear() {
        // 跨年这一步最容易写成「0 月」
        val slots = TrendWindow.lastMonths(2027, 1, 3)
        assertEquals(listOf(YearMonth(2026, 11), YearMonth(2026, 12), YearMonth(2027, 1)), slots)
    }

    @Test
    fun keysStayMonotonicAcrossTheYearBoundary() {
        val keys = TrendWindow.lastMonths(2026, 2, 6).map { it.key }
        assertEquals(keys.sorted(), keys)
        assertEquals(listOf(202509, 202510, 202511, 202512, 202601, 202602), keys)
    }

    @Test
    fun singleMonthWindowIsAllowed() {
        assertEquals(listOf(YearMonth(2026, 11)), TrendWindow.lastMonths(2026, 11, 1))
    }

    @Test
    fun alignFillsMissingMonthsWithZeroWithoutShifting() {
        val slots = TrendWindow.lastMonths(2026, 3, 3)
        val aligned = TrendWindow.align(slots, mapOf(202601 to 12.0, 202603 to 5.0))
        assertEquals(
            listOf(YearMonth(2026, 1) to 12.0, YearMonth(2026, 2) to 0.0, YearMonth(2026, 3) to 5.0),
            aligned
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun countOutOfRangeIsRejected() {
        TrendWindow.lastMonths(2026, 11, 61)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidMonthIsRejected() {
        TrendWindow.lastMonths(2026, 13, 6)
    }
}
