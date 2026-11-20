package com.pixel.moneytracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 记录类型：收入 / 支出
 */
enum class RecordType {
    INCOME, EXPENSE
}

/**
 * 记账记录实体
 *
 * 删除是「软删除」：`deletedAt != null` 表示这条记录躺在回收站里，
 * 所有账目、统计、导出、导入去重的查询都必须排除它（见 RecordDao）。
 * 这样误删一笔还能撤回，而不是直接从账本里消失。
 */
@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(
            entity = Ledger::class,
            parentColumns = ["id"],
            childColumns = ["ledgerId"],
            onDelete = ForeignKey.SET_DEFAULT
        )
    ],
    indices = [Index(value = ["ledgerId"]), Index(value = ["deletedAt"])]
)
data class Record(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,           // 金额（正数）
    val type: RecordType,         // 收入/支出
    val category: String,         // 分类名称
    val emoji: String,            // 分类表情
    val note: String = "",        // 备注
    val year: Int,                // 年
    val month: Int,               // 月
    val day: Int,                 // 日
    val ledgerId: Long = 1,       // 所属账本（默认账本=1）
    val timestamp: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null   // 进回收站的时间；null = 正常记录
)
