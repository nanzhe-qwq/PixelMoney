package com.pixel.moneytracker.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pixel.moneytracker.data.entity.ImportKey

@Dao
interface ImportKeyDao {
    @Query("SELECT * FROM import_keys WHERE `key` IN (:keys)")
    suspend fun find(keys: List<String>): List<ImportKey>

    @Query("SELECT * FROM import_keys")
    suspend fun getAll(): List<ImportKey>

    @Insert
    suspend fun insert(key: ImportKey)

    @Insert
    suspend fun insertAll(keys: List<ImportKey>)
}
