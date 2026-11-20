package com.pixel.moneytracker.data.dao

import androidx.room.*
import com.pixel.moneytracker.data.entity.Budget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    /**
     * 一个月一条预算：(year, month) 唯一索引 + REPLACE，重复保存即覆盖。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget): Long

    @Query("SELECT * FROM budgets WHERE year = :year AND month = :month ORDER BY id DESC LIMIT 1")
    fun getBudget(year: Int, month: Int): Flow<Budget?>

    @Query("SELECT * FROM budgets WHERE year = :year AND month = :month ORDER BY id DESC LIMIT 1")
    suspend fun getBudgetSync(year: Int, month: Int): Budget?

    /** 备份用：一次取全部预算。 */
    @Query("SELECT * FROM budgets ORDER BY year, month")
    suspend fun getAllOnce(): List<Budget>

    /** 删除某月预算（用于「不设预算」）。 */
    @Query("DELETE FROM budgets WHERE year = :year AND month = :month")
    suspend fun deleteByMonth(year: Int, month: Int): Int
}
