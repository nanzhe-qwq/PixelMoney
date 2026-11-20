package com.pixel.moneytracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun toCentsRoundsBinaryNoiseAway() {
        assertEquals(1234L, Money.toCents(12.34))
        // 0.1 + 0.2 = 0.30000000000000004，直接比较会在「8 折线」上抖动
        assertEquals(30L, Money.toCents(0.1 + 0.2))
        assertEquals(0L, Money.toCents(0.0))
        assertEquals(200000L, Money.toCents(2000.0))
    }

    @Test
    fun fromCentsIsExactForTwoDecimals() {
        assertEquals(12.34, Money.fromCents(1234L), 0.0)
        assertEquals(0.01, Money.fromCents(1L), 0.0)
    }

    @Test
    fun parseCentsAcceptsPlainAndTwoDecimalAmounts() {
        assertEquals(200000L, Money.parseCents("2000"))
        assertEquals(200050L, Money.parseCents("2000.5"))
        assertEquals(200055L, Money.parseCents("2000.55"))
        assertEquals(1230L, Money.parseCents(" 12.30 "))
        assertEquals(1L, Money.parseCents("0.01"))
    }

    @Test
    fun parseCentsRejectsAmbiguousOrOutOfRangeInput() {
        assertNull(Money.parseCents(""))
        assertNull(Money.parseCents("   "))
        assertNull(Money.parseCents("0"))
        assertNull(Money.parseCents("0.00"))
        assertNull(Money.parseCents(".5"))
        assertNull(Money.parseCents("2000."))
        assertNull(Money.parseCents("2000.555"))
        assertNull(Money.parseCents("1e5"))
        assertNull(Money.parseCents("-5"))
        assertNull(Money.parseCents("12,34"))
        // 超过 1 亿元上限
        assertNull(Money.parseCents("100000000.01"))
    }

    @Test
    fun parseCentsHonoursCustomCeiling() {
        assertEquals(500L, Money.parseCents("5", maxCents = 500L))
        assertNull(Money.parseCents("5.01", maxCents = 500L))
    }

    @Test
    fun formatNeverUsesScientificNotation() {
        assertEquals("12.34", Money.format(1234L))
        assertEquals("0.00", Money.format(0L))
        assertEquals("100000000.00", Money.format(Money.MAX_CENTS))
    }
}
