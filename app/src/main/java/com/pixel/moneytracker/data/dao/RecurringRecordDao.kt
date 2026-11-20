package com.pixel.moneytracker.data.dao

import androidx.room.*
import com.pixel.moneytracker.data.entity.RecurringRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRecordDao {

    @Insert
    suspend fun insert(record: RecurringRecord): Long

    @Update
    suspend fun update(record: RecurringRecord)

    @Delete
    suspend fun delete(record: RecurringRecord)

    @Query("DELETE FROM recurring_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    // 查询所有自动记账规则
    @Query("SELECT * FROM recurring_records ORDER BY createdAt DESC")
    fun getAll(): Flow<List<RecurringRecord>>

    // 查询所有已启用的规则
    @Query("SELECT * FROM recurring_records WHERE enabled = 1")
    suspend fun getEnabled(): List<RecurringRecord>

    // 更新上次执行时间
    @Query("UPDATE recurring_records SET lastExecutedDate = :timestamp WHERE id = :id")
    suspend fun updateLastExecuted(id: Long, timestamp: Long)

    /**
     * 原子领取一个到期点：只有 lastExecutedDate 仍然等于 expected 时才推进。
     *
     * 返回值 1 = 领取成功（本次由我入账），0 = 已被别的调度/界面抢先执行。
     * 后台闹钟、开机广播、App 打开三处可能并发触发，靠这一条 UPDATE 保证不重复入账，
     * 不需要额外的锁或新字段。
     */
    @Query("UPDATE recurring_records SET lastExecutedDate = :next WHERE id = :id AND lastExecutedDate = :expected")
    suspend fun claimExecution(id: Long, expected: Long, next: Long): Int

    // 切换启用/禁用
    @Query("UPDATE recurring_records SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
