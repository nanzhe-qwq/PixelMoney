package com.pixel.moneytracker.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pixel.moneytracker.service.PendingPayment

@Dao
interface PendingPaymentDao {
    @Query("SELECT * FROM pending_payments WHERE id = :id")
    suspend fun get(id: String): PendingPayment?

    @Query("SELECT * FROM pending_payments WHERE status = 'PENDING' AND expiresAt > :now ORDER BY createdAt")
    suspend fun available(now: Long): List<PendingPayment>

    @Upsert
    suspend fun put(payment: PendingPayment)
}
