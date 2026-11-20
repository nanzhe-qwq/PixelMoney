package com.pixel.moneytracker.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DateUtil {
    fun currentYear() = java.time.LocalDate.now().year
    fun currentMonth() = java.time.LocalDate.now().monthValue
    fun currentDay() = java.time.LocalDate.now().dayOfMonth

    fun daysInMonth(year: Int, month: Int): Int {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun monthName(month: Int): String = when (month) {
        1 -> "一月"; 2 -> "二月"; 3 -> "三月"; 4 -> "四月"
        5 -> "五月"; 6 -> "六月"; 7 -> "七月"; 8 -> "八月"
        9 -> "九月"; 10 -> "十月"; 11 -> "十一月"; 12 -> "十二月"
        else -> ""
    }

    fun formatDate(year: Int, month: Int, day: Int): String =
        "${year}年${month}月${day}日"

    /**
     * 金额归整到「分」。
     * 数据库里金额是 Double(REAL)，单条记录的二进制误差约 1e-13，
     * 累加后会在展示时冒出 ¥1234.5599999 这种尾巴。统一在聚合出口归整一次，
     * 结果与「整数分」存储等价（1000 条记录的累计误差仍小于 1e-9 分）。
     */
    fun round2(amount: Double): Double = Math.round(amount * 100.0) / 100.0

    fun formatAmount(amount: Double): String =
        String.format("¥%.2f", round2(amount))

    /**
     * 紧凑金额格式（日历用）：12.5 / 120 / 1.2k
     */
    fun formatAmountCompact(amount: Double): String = when {
        amount >= 10000 -> String.format("%.1fw", amount / 10000)
        amount >= 1000 -> String.format("%.1fk", amount / 1000)
        amount == amount.toLong().toDouble() -> "${amount.toLong()}"
        // 向下截断到 0.1，避免 99.99 在日历格子里显示成「100.0」造成误读
        else -> String.format("%.1f", Math.floor(amount * 10) / 10.0)
    }
}
