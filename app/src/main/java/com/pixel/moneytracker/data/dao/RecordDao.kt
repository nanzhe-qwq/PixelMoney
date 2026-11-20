package com.pixel.moneytracker.data.dao

import androidx.room.*
import com.pixel.moneytracker.data.entity.Record
import com.pixel.moneytracker.data.entity.RecordType
import kotlinx.coroutines.flow.Flow

/**
 * 账单表访问。
 *
 * 约定：**除了回收站相关的查询，每个查询都必须带 `deletedAt IS NULL`**。
 * 少一处，回收站里的账单就会重新出现在首页、统计、预算和导出里。
 */
@Dao
interface RecordDao {

    @Insert
    suspend fun insert(record: Record): Long

    @Update
    suspend fun update(record: Record)

    @Delete
    suspend fun delete(record: Record)

    /** 只做真正的物理删除；日常删除走 [moveToTrash]。 */
    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ====== 回收站 ======

    /** 软删除：只在它还是正常记录时打时间戳，重复调用不会刷新保留期。返回 1 表示真的挪走了。 */
    @Query("UPDATE records SET deletedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun moveToTrash(id: Long, at: Long): Int

    @Query("UPDATE records SET deletedAt = NULL WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun restoreFromTrash(id: Long): Int

    /** 从回收站彻底删除（不在回收站里的 id 不会被误删）。 */
    @Query("DELETE FROM records WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun purge(id: Long): Int

    @Query("DELETE FROM records WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllTrash(): Int

    /** 超过保留期的回收站记录。 */
    @Query("DELETE FROM records WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeTrashOlderThan(cutoff: Long): Int

    @Query("SELECT * FROM records WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getTrash(): Flow<List<Record>>

    @Query("SELECT * FROM records WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    suspend fun getTrashOnce(): List<Record>

    @Query("SELECT COUNT(*) FROM records WHERE deletedAt IS NOT NULL")
    fun getTrashCount(): Flow<Int>

    // ====== 按账本+月份查询 ======

    @Query("SELECT * FROM records WHERE ledgerId = :ledgerId AND year = :year AND month = :month AND deletedAt IS NULL ORDER BY day DESC, timestamp DESC")
    fun getRecordsByLedgerAndMonth(ledgerId: Long, year: Int, month: Int): Flow<List<Record>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM records WHERE ledgerId = :ledgerId AND year = :year AND month = :month AND type = 'EXPENSE' AND deletedAt IS NULL")
    fun getMonthExpenseByLedger(ledgerId: Long, year: Int, month: Int): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM records WHERE ledgerId = :ledgerId AND year = :year AND month = :month AND type = 'INCOME' AND deletedAt IS NULL")
    fun getMonthIncomeByLedger(ledgerId: Long, year: Int, month: Int): Flow<Double>

    /** 供预算提醒做一次性判断用（非 Flow，避免在通知逻辑里挂 collect）。 */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM records WHERE ledgerId = :ledgerId AND year = :year AND month = :month AND type = 'EXPENSE' AND deletedAt IS NULL")
    suspend fun getMonthExpenseByLedgerSync(ledgerId: Long, year: Int, month: Int): Double

    @Query("SELECT category, emoji, SUM(amount) as total FROM records WHERE ledgerId = :ledgerId AND year = :year AND month = :month AND type = 'EXPENSE' AND deletedAt IS NULL GROUP BY category ORDER BY total DESC")
    fun getExpenseByCategoryLedger(ledgerId: Long, year: Int, month: Int): Flow<List<CategorySummary>>

    @Query("SELECT category, emoji, SUM(amount) as total FROM records WHERE ledgerId = :ledgerId AND year = :year AND month = :month AND type = 'INCOME' AND deletedAt IS NULL GROUP BY category ORDER BY total DESC")
    fun getIncomeByCategoryLedger(ledgerId: Long, year: Int, month: Int): Flow<List<CategorySummary>>

    @Query("SELECT day, SUM(amount) as total FROM records WHERE ledgerId = :ledgerId AND year = :year AND month = :month AND type = 'EXPENSE' AND deletedAt IS NULL GROUP BY day ORDER BY day")
    fun getDailyExpenseByLedger(ledgerId: Long, year: Int, month: Int): Flow<List<DailySummary>>

    /**
     * 跨月趋势：把 (year, month) 压成 year*100+month 整数做区间比较，
     * 一次查回连续若干个月的支出合计，供「近 6 个月」柱状图使用。
     */
    @Query("SELECT year, month, SUM(amount) as total FROM records WHERE ledgerId = :ledgerId AND type = 'EXPENSE' AND deletedAt IS NULL AND (year * 100 + month) BETWEEN :fromYearMonth AND :toYearMonth GROUP BY year, month ORDER BY year, month")
    fun getMonthlyExpense(ledgerId: Long, fromYearMonth: Int, toYearMonth: Int): Flow<List<MonthSummary>>

    @Query("SELECT year, month, SUM(amount) as total FROM records WHERE ledgerId = :ledgerId AND type = 'INCOME' AND deletedAt IS NULL AND (year * 100 + month) BETWEEN :fromYearMonth AND :toYearMonth GROUP BY year, month ORDER BY year, month")
    fun getMonthlyIncome(ledgerId: Long, fromYearMonth: Int, toYearMonth: Int): Flow<List<MonthSummary>>

    // ====== 兼容旧接口（全局查询） ======

    @Query("SELECT * FROM records WHERE year = :year AND month = :month AND deletedAt IS NULL ORDER BY day DESC, timestamp DESC")
    fun getRecordsByMonth(year: Int, month: Int): Flow<List<Record>>

    @Query("SELECT * FROM records WHERE year = :year AND month = :month AND day = :day AND deletedAt IS NULL ORDER BY timestamp DESC")
    fun getRecordsByDay(year: Int, month: Int, day: Int): Flow<List<Record>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM records WHERE year = :year AND month = :month AND type = 'EXPENSE' AND deletedAt IS NULL")
    fun getMonthExpense(year: Int, month: Int): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM records WHERE year = :year AND month = :month AND type = 'INCOME' AND deletedAt IS NULL")
    fun getMonthIncome(year: Int, month: Int): Flow<Double>

    @Query("SELECT category, emoji, SUM(amount) as total FROM records WHERE year = :year AND month = :month AND type = 'EXPENSE' AND deletedAt IS NULL GROUP BY category ORDER BY total DESC")
    fun getExpenseByCategory(year: Int, month: Int): Flow<List<CategorySummary>>

    @Query("SELECT category, emoji, SUM(amount) as total FROM records WHERE year = :year AND month = :month AND type = 'INCOME' AND deletedAt IS NULL GROUP BY category ORDER BY total DESC")
    fun getIncomeByCategory(year: Int, month: Int): Flow<List<CategorySummary>>

    @Query("SELECT day, SUM(amount) as total FROM records WHERE year = :year AND month = :month AND type = 'EXPENSE' AND deletedAt IS NULL GROUP BY day ORDER BY day")
    fun getDailyExpense(year: Int, month: Int): Flow<List<DailySummary>>

    @Query("SELECT * FROM records WHERE deletedAt IS NULL ORDER BY year DESC, month DESC, day DESC, timestamp DESC")
    fun getAllRecords(): Flow<List<Record>>

    /** 备份用：包含回收站里的记录，否则换机后回收站会凭空清空。 */
    @Query("SELECT * FROM records ORDER BY year DESC, month DESC, day DESC, timestamp DESC")
    suspend fun getAllRecordsRaw(): List<Record>

    // ====== 统计用 ======

    @Query("SELECT COUNT(*) FROM records WHERE deletedAt IS NULL")
    fun getTotalRecordCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT (year * 10000 + month * 100 + day)) FROM records WHERE deletedAt IS NULL")
    fun getTotalRecordingDays(): Flow<Int>

    @Query("SELECT DISTINCT year * 10000 + month * 100 + day AS dateNum FROM records WHERE deletedAt IS NULL ORDER BY dateNum DESC")
    fun getAllRecordDates(): Flow<List<Int>>
}

data class CategorySummary(
    val category: String,
    val emoji: String,
    val total: Double
)

data class DailySummary(
    val day: Int,
    val total: Double
)

data class MonthSummary(
    val year: Int,
    val month: Int,
    val total: Double
)
