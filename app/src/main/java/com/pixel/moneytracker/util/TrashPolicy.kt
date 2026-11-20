package com.pixel.moneytracker.util

/**
 * 回收站保留策略。
 *
 * 误删一笔账的最高频场景是「手滑点了 ×」，所以删除不直接落成物理删除：
 * 先进回收站，[RETENTION_DAYS] 天内可以随时恢复，过期才真正清理。
 * 保留期判断全部在这里做，UI 和清理逻辑共用同一套口径。
 */
object TrashPolicy {

    const val RETENTION_DAYS = 30

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** 早于这个时间点进回收站的记录可以物理删除。 */
    fun cutoff(now: Long): Long = now - RETENTION_DAYS * DAY_MS

    /**
     * 还能恢复几天：刚删除当天返回 30，最后一天返回 1，已过期返回 0。
     * 用毫秒差而不是日历天，避免时区与夏令时干扰。
     */
    fun remainingDays(deletedAt: Long, now: Long): Int {
        val elapsed = now - deletedAt
        if (elapsed < 0) return RETENTION_DAYS
        val remain = RETENTION_DAYS * DAY_MS - elapsed
        if (remain <= 0) return 0
        return ((remain + DAY_MS - 1) / DAY_MS).toInt()
    }

    fun isExpired(deletedAt: Long, now: Long): Boolean = remainingDays(deletedAt, now) == 0
}
