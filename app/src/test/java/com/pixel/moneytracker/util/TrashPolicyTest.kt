package com.pixel.moneytracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashPolicyTest {

    private val day = 24L * 60L * 60L * 1000L
    private val now = 1_800_000_000_000L

    @Test
    fun freshDeleteKeepsFullRetention() {
        assertEquals(30, TrashPolicy.remainingDays(now, now))
        assertEquals(30, TrashPolicy.remainingDays(now - 1000L, now))
    }

    @Test
    fun remainingDaysShrinkByWholeDays() {
        assertEquals(29, TrashPolicy.remainingDays(now - day, now))
        assertEquals(1, TrashPolicy.remainingDays(now - 29 * day, now))
        // 差一秒还没到期：仍算 1 天，不能提前清掉
        assertEquals(1, TrashPolicy.remainingDays(now - 30 * day + 1000L, now))
    }

    @Test
    fun exactlyAtCutoffIsExpired() {
        assertEquals(0, TrashPolicy.remainingDays(now - 30 * day, now))
        assertTrue(TrashPolicy.isExpired(now - 30 * day, now))
        assertFalse(TrashPolicy.isExpired(now - 30 * day + 1000L, now))
    }

    @Test
    fun cutoffMatchesRetentionWindow() {
        assertEquals(now - 30 * day, TrashPolicy.cutoff(now))
    }

    @Test
    fun futureTimestampDoesNotOverflowRetention() {
        // 系统时间被改回去时，别把剩余天数算成 31 天或负数
        assertEquals(TrashPolicy.RETENTION_DAYS, TrashPolicy.remainingDays(now + 5 * day, now))
    }
}
