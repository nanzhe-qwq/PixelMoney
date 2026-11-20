package com.pixel.moneytracker.util

/**
 * 预算提醒的阈值级别。percent 同时是「去重水位」：只允许向更高的级别推进。
 */
enum class BudgetLevel(val percent: Int) {
    /** 已用达到 80%（含）。 */
    WARNING(80),

    /** 本月支出已经吃掉全部预算（含正好用完）。 */
    EXCEEDED(100)
}

/**
 * 纯逻辑的预算判定，方便在 JVM 单测里覆盖边界。
 *
 * 提醒去重策略：同一个 (年, 月) 只记录「已经提醒到的最高级别」。
 * 到达 80% 提醒一次；之后继续记账到 100% 再提醒一次；再往上（120%、200%）
 * 都不会重复提醒，同一月份内同一阈值最多一条通知。
 */
internal object BudgetPolicy {

    /** 判定当前应该处于哪一档；没有预算（<=0）时返回 null。 */
    fun level(budgetCents: Long, spentCents: Long): BudgetLevel? {
        if (budgetCents <= 0L || spentCents < 0L) return null
        // 整数比较，避免 spent/budget 的浮点百分比在边界抖动。
        return when {
            spentCents >= budgetCents -> BudgetLevel.EXCEEDED
            spentCents * 100L >= budgetCents * BudgetLevel.WARNING.percent -> BudgetLevel.WARNING
            else -> null
        }
    }

    /**
     * @param lastNotifiedPercent 本预算周期已经提醒到的级别（0 表示还没提醒过）
     * @return 需要提醒的级别；同一或更低级别返回 null
     */
    fun shouldNotify(level: BudgetLevel?, lastNotifiedPercent: Int): BudgetLevel? =
        level?.takeIf { it.percent > lastNotifiedPercent }

    /** 已用百分比（整数，向下取整，用于展示）。 */
    fun usedPercent(budgetCents: Long, spentCents: Long): Int {
        if (budgetCents <= 0L) return 0
        return ((spentCents * 100L) / budgetCents).coerceIn(0L, 9999L).toInt()
    }
}
