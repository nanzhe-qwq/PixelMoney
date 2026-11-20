package com.pixel.moneytracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetPolicyTest {

    private val budget = 200000L // ¥2000.00

    @Test
    fun noBudgetMeansNoLevel() {
        assertNull(BudgetPolicy.level(0L, 1000L))
        assertNull(BudgetPolicy.level(-1L, 1000L))
        assertNull(BudgetPolicy.level(budget, -1L))
    }

    @Test
    fun eightyPercentIsInclusive() {
        assertNull(BudgetPolicy.level(budget, 159999L))
        assertEquals(BudgetLevel.WARNING, BudgetPolicy.level(budget, 160000L))
        assertEquals(BudgetLevel.WARNING, BudgetPolicy.level(budget, 199999L))
    }

    @Test
    fun exactlySpentIsExceeded() {
        // 正好花完属于「超支」档，与首页「剩余 0 = 用完」的展示口径不同，这里只决定提醒
        assertEquals(BudgetLevel.EXCEEDED, BudgetPolicy.level(budget, 200000L))
        assertEquals(BudgetLevel.EXCEEDED, BudgetPolicy.level(budget, 800000L))
    }

    @Test
    fun eachThresholdNotifiesOnlyOncePerPeriod() {
        assertEquals(BudgetLevel.WARNING, BudgetPolicy.shouldNotify(BudgetLevel.WARNING, 0))
        assertNull(BudgetPolicy.shouldNotify(BudgetLevel.WARNING, BudgetLevel.WARNING.percent))
        assertEquals(BudgetLevel.EXCEEDED, BudgetPolicy.shouldNotify(BudgetLevel.EXCEEDED, BudgetLevel.WARNING.percent))
        // 同一周期已经提醒到 100% 后，再记账不会重复弹
        assertNull(BudgetPolicy.shouldNotify(BudgetLevel.EXCEEDED, BudgetLevel.EXCEEDED.percent))
        assertNull(BudgetPolicy.shouldNotify(null, 0))
    }

    @Test
    fun usedPercentIsFloorAndSafeWithoutBudget() {
        assertEquals(80, BudgetPolicy.usedPercent(budget, 160000L))
        assertEquals(0, BudgetPolicy.usedPercent(budget, 1L))
        assertEquals(100, BudgetPolicy.usedPercent(budget, budget))
        assertEquals(0, BudgetPolicy.usedPercent(0L, 5000L))
    }
}
