package com.pixel.moneytracker.util

/** 趋势图上的一个月份槽位。 */
data class YearMonth(val year: Int, val month: Int) {
    /** 压成整数便于 SQL 区间比较：2026-11 -> 202611（月份补两位，跨年也单调）。 */
    val key: Int get() = year * 100 + month

    val label: String get() = "$month 月"
}

/**
 * 趋势窗口计算。
 *
 * 统计页要「以当前选中月为终点，往前数 N 个月」，包括跨年。
 * 放到这里是为了能纯函数测试——跨年那一步（1 月往前是上一年 12 月）最容易写错。
 */
object TrendWindow {

    fun lastMonths(endYear: Int, endMonth: Int, count: Int): List<YearMonth> {
        require(count in 1..60) { "count 必须在 1..60 之间" }
        require(endMonth in 1..12) { "month 必须在 1..12 之间" }
        val result = ArrayList<YearMonth>(count)
        var year = endYear
        var month = endMonth
        repeat(count) {
            result.add(YearMonth(year, month))
            if (month == 1) {
                month = 12
                year -= 1
            } else {
                month -= 1
            }
        }
        return result.asReversed()
    }

    /**
     * 把 SQL 查回来的稀疏结果对齐到完整槽位列表上：没有记录的月份补 0，
     * 否则柱状图会少一根柱子、标签和数值错位。
     */
    fun align(slots: List<YearMonth>, totals: Map<Int, Double>): List<Pair<YearMonth, Double>> =
        slots.map { it to (totals[it.key] ?: 0.0) }
}
