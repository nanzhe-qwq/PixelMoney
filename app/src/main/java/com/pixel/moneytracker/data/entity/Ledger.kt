package com.pixel.moneytracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ledgers")
data class Ledger(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,           // 账本名称
    val emoji: String,          // 账本表情
    val isDefault: Boolean = false,  // 是否默认账本
    val createdAt: Long = System.currentTimeMillis()
)
