package com.pixel.moneytracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 自动记账频率
 */
enum class Frequency(val label: String, val days: Int) {
    DAILY("每天", 1),
    WEEKLY("每周", 7),
    MONTHLY("每月", 30),
    YEARLY("每年", 365)
}

/**
 * 自动记账规则
 *
 * `ledgerId` 记录规则创建时所在的账本（schema 7 起）。旧代码把到期账单写进
 * 「当时打开 App 选中的账本」，同一个规则会随用户切账本而漂移；后台执行时更是拿不到
 * 界面状态，所以把账本固化到规则上。老规则由迁移统一落到 1（日常开销）。
 */
@Entity(tableName = "recurring_records")
data class RecurringRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val type: RecordType,
    val category: String,
    val emoji: String,
    val note: String = "",
    val frequency: Frequency,
    val enabled: Boolean = true,
    val lastExecutedDate: Long = 0L,  // 上次执行的时间戳
    val createdAt: Long = System.currentTimeMillis(),
    val ledgerId: Long = 1L
)
