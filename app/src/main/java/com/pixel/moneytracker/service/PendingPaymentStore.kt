package com.pixel.moneytracker.service

import java.util.UUID

/** Payload is read from local persistence, never trusted from a stale PendingIntent. */
@androidx.room.Entity(tableName = "pending_payments")
data class PendingPayment(
    @androidx.room.PrimaryKey val id: String,
    val amount: Double,
    val source: String,
    val note: String,
    val isIncome: Boolean,
    val createdAt: Long,
    val expiresAt: Long,
    val status: String = "PENDING"
)

data class PaymentEdit(val category: String, val emoji: String, val note: String, val isIncome: Boolean)
enum class PaymentResolution { APPLIED, UNAVAILABLE }

interface PendingPaymentTransaction {
    suspend fun insertRecord(payment: PendingPayment, edit: PaymentEdit)
    suspend fun get(id: String): PendingPayment?
    suspend fun put(payment: PendingPayment)
}

/** Implementations MUST commit/roll back all candidate and record writes in one transaction. */
interface PendingPaymentBackend {
    suspend fun <T> transaction(block: suspend PendingPaymentTransaction.() -> T): T
}

class PendingPaymentStore(
    private val backend: PendingPaymentBackend,
    private val now: () -> Long = System::currentTimeMillis
) {
    companion object { const val TTL_MS = 24 * 60 * 60 * 1000L }

    suspend fun create(amount: Double, source: String, note: String, isIncome: Boolean): PendingPayment {
        require(amount.isFinite() && amount > 0)
        val time = now()
        val candidate = PendingPayment(UUID.randomUUID().toString(), amount, source, note, isIncome, time, time + TTL_MS)
        backend.transaction { put(candidate) }
        return candidate
    }

    private fun PendingPayment.isAvailable() = status == "PENDING" && now() < expiresAt

    suspend fun load(id: String): PendingPayment? = backend.transaction { get(id)?.takeIf { it.isAvailable() } }

    suspend fun confirm(id: String, edit: PaymentEdit): PaymentResolution = backend.transaction {
        val candidate = get(id)?.takeIf { it.isAvailable() } ?: return@transaction PaymentResolution.UNAVAILABLE
        insertRecord(candidate, edit)
        put(candidate.copy(status = "CONFIRMED"))
        PaymentResolution.APPLIED
    }

    suspend fun ignore(id: String): PaymentResolution = backend.transaction {
        val candidate = get(id)?.takeIf { it.isAvailable() } ?: return@transaction PaymentResolution.UNAVAILABLE
        put(candidate.copy(status = "IGNORED"))
        PaymentResolution.APPLIED
    }
}
