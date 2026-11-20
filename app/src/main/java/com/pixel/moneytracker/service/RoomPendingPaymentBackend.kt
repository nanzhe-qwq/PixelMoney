package com.pixel.moneytracker.service

import androidx.room.withTransaction
import com.pixel.moneytracker.data.database.AppDatabase
import com.pixel.moneytracker.data.entity.Record
import com.pixel.moneytracker.data.entity.RecordType
import java.util.Calendar

/** Room serializes both entrances. A killed process commits both writes or neither. */
class RoomPendingPaymentBackend(private val db: AppDatabase) : PendingPaymentBackend {
    override suspend fun <T> transaction(block: suspend PendingPaymentTransaction.() -> T): T = db.withTransaction {
        val tx = object : PendingPaymentTransaction {
            override suspend fun get(id: String) = db.pendingPaymentDao().get(id)
            override suspend fun put(payment: PendingPayment) = db.pendingPaymentDao().put(payment)
            override suspend fun insertRecord(payment: PendingPayment, edit: PaymentEdit) {
                val cal = Calendar.getInstance()
                db.recordDao().insert(Record(
                    amount = payment.amount,
                    type = if (edit.isIncome) RecordType.INCOME else RecordType.EXPENSE,
                    category = edit.category,
                    emoji = edit.emoji,
                    note = "[${payment.source}] ${edit.note}",
                    year = cal.get(Calendar.YEAR),
                    month = cal.get(Calendar.MONTH) + 1,
                    day = cal.get(Calendar.DAY_OF_MONTH)
                ))
            }
        }
        tx.block()
    }
}
