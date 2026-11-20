package com.pixel.moneytracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {

    @Test
    fun parseDistinguishesWholeNumbersFromDecimals() {
        assertEquals(42L, MiniJson.parse("42"))
        assertEquals(-7L, MiniJson.parse("-7"))
        assertEquals(0L, MiniJson.parse("0"))
        assertEquals(1.5, MiniJson.parse("1.5") as Double, 0.0)
        assertEquals(1.0E3, MiniJson.parse("1e3") as Double, 0.0)
    }

    @Test
    fun parseHandlesNestedObjectsAndArrays() {
        val parsed = MiniJson.parse("""{"a":[1,{"b":"x"}],"c":null,"d":true}""") as Map<*, *>
        assertEquals(listOf(1L, mapOf("b" to "x")), parsed["a"])
        assertNull(parsed["c"])
        assertEquals(true, parsed["d"])
    }

    @Test
    fun escapeThenParseRoundTripsAwkwardText() {
        val original = "引号\"反斜杠\\换行\n制表\t中文¥1.00\u0001"
        val encoded = MiniJson.stringify(mapOf("note" to original))
        val decoded = MiniJson.parse(encoded) as Map<*, *>
        assertEquals(original, decoded["note"])
    }

    @Test
    fun stringifyWritesWholeDoublesWithoutDecimalPoint() {
        // 金额 7.0 若写成 7.0，回读会变 Double 而不是 Long，比较时会漏掉相等判断
        assertEquals("""{"amount":7}""", MiniJson.stringify(mapOf("amount" to 7.0)))
        assertEquals("""[1,2]""", MiniJson.stringify(listOf(1, 2)))
    }

    @Test(expected = MiniJson.JsonException::class)
    fun trailingContentIsRejected() {
        MiniJson.parse("""{"a":1} {"b":2}""")
    }

    @Test(expected = MiniJson.JsonException::class)
    fun rawControlCharacterIsRejected() {
        MiniJson.parse("\"a\u0001b\"")
    }

    @Test(expected = MiniJson.JsonException::class)
    fun invalidEscapeIsRejected() {
        MiniJson.parse(""" "a\qb" """.trim())
    }

    @Test(expected = MiniJson.JsonException::class)
    fun deeplyNestedInputIsRejected() {
        MiniJson.parse("[".repeat(MiniJson.MAX_DEPTH + 4) + "]".repeat(MiniJson.MAX_DEPTH + 4))
    }

    @Test
    fun nonFiniteNumbersCannotBeEncoded() {
        val threw = try {
            MiniJson.stringify(mapOf("v" to Double.NaN))
            false
        } catch (_: MiniJson.JsonException) {
            true
        }
        assertTrue("NaN 必须拒绝，否则备份文件里会出现非法的 NaN 字面量", threw)
    }
}
