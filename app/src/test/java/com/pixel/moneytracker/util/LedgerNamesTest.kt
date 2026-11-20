package com.pixel.moneytracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerNamesTest {

    @Test
    fun normalizeTrimsAndCollapsesWhitespace() {
        assertEquals("旅行基金", LedgerNames.normalize("  旅行基金  "))
        assertEquals("旅行 基金", LedgerNames.normalize("旅行   基金"))
        assertEquals("a b", LedgerNames.normalize("\ta\nb "))
    }

    @Test
    fun blankNameIsRejected() {
        assertNotNull(LedgerNames.validate(""))
        assertNotNull(LedgerNames.validate("     "))
        assertEquals("账本名称不能为空", LedgerNames.validate("  "))
    }

    @Test
    fun lengthLimitCountsNormalizedCharacters() {
        assertEquals(20, LedgerNames.MAX_LENGTH)
        assertNull(LedgerNames.validate("一二三四五六七八九十一二三四五六七八九十"))
        assertNotNull(LedgerNames.validate("一二三四五六七八九十一二三四五六七八九十一"))
        // 首尾空格不占长度
        assertNull(LedgerNames.validate("  " + "a".repeat(20) + "  "))
    }
}
