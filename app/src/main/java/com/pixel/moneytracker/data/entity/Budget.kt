package com.pixel.moneytracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 预算实体
 *
 * `(year, month)` 上有唯一索引（schema 7 起）：一个月只允许一条预算。
 * 这样 [com.pixel.moneytracker.data.dao.BudgetDao.insert] 的 `REPLACE` 语义才是
 * 「改预算」而不是「追加一条预算」。加索引前已有的重复行由 MIGRATION_6_7 清理。
 */
@Entity(
    tableName = "budgets",
    indices = [Index(value = ["year", "month"], unique = true)]
)
data class Budget(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val year: Int,
    val month: Int,
    val amount: Double   // 月预算金额
)
