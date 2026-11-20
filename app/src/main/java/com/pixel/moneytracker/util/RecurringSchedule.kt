package com.pixel.moneytracker.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 一次到期的周期记账。 */
internal data class RecurringOccurrence(
    /** 到期时刻（入账记录的 timestamp）。 */
    val at: Long,
    /** 到期时刻在时区内的自然日（入账记录的 year/month/day）。 */
    val date: LocalDate,
    /** 是否落在补记窗口内。窗口外的到期点只推进 lastExecutedDate，不补账单。 */
    val withinWindow: Boolean
)

/**
 * 周期记账的纯时间计算，便于在 JVM 单测里覆盖边界。
 *
 * 补记窗口（[MAX_CATCH_UP_DAYS] = 62 天）的理由：
 * - 覆盖「手机整月没开机」「出差跨两个月」这两种常见漏记；
 * - 又不会在用户换机/时钟错乱后，把一两年前的规则一次性灌进账本。
 * 单次最多补 [MAX_CATCH_UP_OCCURRENCES] 笔，是防止某条规则被设成「每天」后
 * 首次调度写入过多记录；超出的部分由下一次调度继续推进，不会丢。
 */
internal object RecurringSchedule {

    const val MAX_CATCH_UP_DAYS = 62L
    const val MAX_CATCH_UP_OCCURRENCES = 12

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    private const val MIN_DELAY_MILLIS = 60L * 1000L

    /**
     * 返回本次应该处理的到期点（按时间升序）。
     *
     * - `lastExecuted <= 0`：规则刚创建，按旧行为「立即执行一次」，返回单个 now；
     * - `lastExecuted > now`：时钟被回拨，什么都不做（返回空，避免重复入账）；
     * - 否则从 `lastExecuted + interval` 递增到 now，最多 [MAX_CATCH_UP_OCCURRENCES] 条。
     */
    fun occurrences(lastExecuted: Long, intervalDays: Int, now: Long, zone: ZoneId): List<RecurringOccurrence> {
        require(intervalDays >= 1) { "intervalDays must be positive" }
        if (lastExecuted <= 0L) {
            val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            return listOf(RecurringOccurrence(now, date, true))
        }
        if (lastExecuted > now) return emptyList()

        val interval = intervalDays * DAY_MILLIS
        val earliest = now - MAX_CATCH_UP_DAYS * DAY_MILLIS
        val result = ArrayList<RecurringOccurrence>(4)
        var at = lastExecuted + interval
        while (at <= now && result.size < MAX_CATCH_UP_OCCURRENCES) {
            result += RecurringOccurrence(
                at = at,
                date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate(),
                withinWindow = at >= earliest
            )
            at += interval
        }
        return result
    }

    /**
     * 下一次该检查的时刻。到期点已经过去（比如刚补完账）时给一个最小延迟，
     * 避免 AlarmManager 立刻回调形成空转。
     */
    fun nextCheckAt(lastExecuted: Long, intervalDays: Int, now: Long): Long {
        require(intervalDays >= 1) { "intervalDays must be positive" }
        val soonest = now + MIN_DELAY_MILLIS
        if (lastExecuted <= 0L) return soonest
        val next = lastExecuted + intervalDays * DAY_MILLIS
        return if (next < soonest) soonest else next
    }
}
