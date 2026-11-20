package com.pixel.moneytracker.data.dao

import androidx.room.*
import com.pixel.moneytracker.data.entity.Ledger
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    @Insert
    suspend fun insert(ledger: Ledger): Long

    @Update
    suspend fun update(ledger: Ledger)

    @Delete
    suspend fun delete(ledger: Ledger)

    @Query("DELETE FROM ledgers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM ledgers ORDER BY isDefault DESC, createdAt ASC")
    fun getAll(): Flow<List<Ledger>>

    @Query("SELECT * FROM ledgers WHERE id = :id")
    suspend fun getById(id: Long): Ledger?

    @Query("SELECT * FROM ledgers WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): Ledger?

    @Query("UPDATE ledgers SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("SELECT COUNT(*) FROM records WHERE ledgerId = :ledgerId")
    suspend fun getRecordCount(ledgerId: Long): Int
}
