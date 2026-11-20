package com.pixel.moneytracker.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 金额换算。
 *
 * 数据库里的金额是 REAL(Double)，但所有「比较、阈值、判断是否超支」都在整数「分」上做，
 * 避免 0.1 + 0.2 这类二进制误差把正好 80% 的账判成 79.99999%。
 * 展示仍然走 [DateUtil.formatAmount]，两者的归整口径一致（四舍五入到分）。
 */
internal object Money {

    /** 单笔 / 预算上限：1 亿元。超出范围视为输入错误，不落库。 */
    const val MAX_CENTS = 10_000_000_000L

    // 整数部分至少一位、小数部分必须跟在点后且 1~2 位：拒绝 ".5"、"2000."、"1e5"
    private val DECIMAL = Regex("""\d{1,9}(?:\.\d{1,2})?""")

    fun toCents(amount: Double): Long = Math.round(amount * 100.0)

    fun fromCents(cents: Long): Double = cents / 100.0

    /**
     * 严格解析用户输入的金额：只接受十进制、最多两位小数、必须大于 0 且不超过 [MAX_CENTS]。
     * 不做 `toDoubleOrNull` 那样的宽松接受（"1e5"、".5"、"." 都会被拒）。
     */
    fun parseCents(text: String, maxCents: Long = MAX_CENTS): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !DECIMAL.matches(trimmed)) return null
        val cents = try {
            BigDecimal(trimmed).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        } catch (_: ArithmeticException) {
            return null
        } catch (_: NumberFormatException) {
            return null
        }
        return cents.takeIf { it > 0 && it <= maxCents }
    }

    /** 与 [BigDecimal] 一致的分转字符串，不带科学计数法。 */
    fun format(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()
}
