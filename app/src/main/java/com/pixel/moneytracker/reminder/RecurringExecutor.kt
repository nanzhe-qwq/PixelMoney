package com.pixel.moneytracker.reminder

import android.content.Context
import androidx.room.withTransaction
import com.pixel.moneytracker.data.database.AppDatabase
import com.pixel.moneytracker.data.entity.Record
import com.pixel.moneytracker.util.RecurringSchedule
import java.time.ZoneId

/**
 * 周期记账的执行器。
 *
 * 三处调用：AlarmManager 闹钟、开机/换时区广播、App 打开（兜底）。
 * 并发安全靠 [com.pixel.moneytracker.data.dao.RecurringRecordDao.claimExecution]
 * 这条「条件更新」：只有把 lastExecutedDate 从旧值推进成功的那一方才入账，
 * 其它并发方拿到 0 行更新就直接放弃这一条规则，因此不会重复入账，
 * 也不需要给 recurring_records 加「执行中」状态列。
 */
internal object RecurringExecutor {

    /**
     * @return 本次实际写入的账单条数
     */
    suspend fun run(context: Context, now: Long = System.currentTimeMillis()): Int {
        val db = AppDatabase.getDatabase(context)
        val zone = ZoneId.systemDefault()
        var inserted = 0
        val touched = mutableSetOf<Triple<Long, Int, Int>>() // ledgerId, year, month

        for (rule in db.recurringRecordDao().getEnabled()) {
            if (rule.ledgerId <= 0L) continue
            var expected = rule.lastExecutedDate
            val due = RecurringSchedule.occurrences(expected, rule.frequency.days, now, zone)
            for (occurrence in due) {
                val date = occurrence.date
                val claimed = db.withTransaction {
                    if (db.recurringRecordDao().claimExecution(rule.id, expected, occurrence.at) != 1) {
                        return@withTransaction false
                    }
                    if (occurrence.withinWindow) {
                        db.recordDao().insert(
                            Record(
                                amount = rule.amount,
                                type = rule.type,
                                category = rule.category,
                                emoji = rule.emoji,
                                note = if (rule.note.isBlank()) "[自动]" else "[自动] ${rule.note}",
                                year = date.year,
                                month = date.monthValue,
                                day = date.dayOfMonth,
                                ledgerId = rule.ledgerId,
                                timestamp = occurrence.at
                            )
                        )
                        touched += Triple(rule.ledgerId, date.year, date.monthValue)
                    }
                    true
                }
                // 没领到：说明这条规则已经被另一处推进了，本轮的到期列表已经过期。
                if (!claimed) break
                expected = occurrence.at
                if (occurrence.withinWindow) inserted++
            }
        }

        // 补记出来的账也会影响预算进度，按受影响的月份补一次判定（内部自带去重）。
        for ((ledgerId, year, month) in touched) {
            runCatching { BudgetAlert.evaluate(context, ledgerId, year, month) }
        }
        return inserted
    }
}
