package com.pixel.moneytracker.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Transactional in-memory implementation, shared across store instances in contract tests. */
class MemoryPendingPaymentBackend : PendingPaymentBackend {
    val records = mutableListOf<Pair<PendingPayment, PaymentEdit>>()
    var failAfterRecordWrite = false
    private val mutex = Mutex()
    private val candidates = mutableMapOf<String, PendingPayment>()
    override suspend fun <T> transaction(block: suspend PendingPaymentTransaction.() -> T): T = mutex.withLock {
        val staged = candidates.toMutableMap()
        val stagedRecords = records.toMutableList()
        val tx = object : PendingPaymentTransaction {
            override suspend fun insertRecord(payment: PendingPayment, edit: PaymentEdit) {
                stagedRecords.add(payment to edit)
                check(!failAfterRecordWrite) { "Injected failure after record write, before candidate consumption" }
            }
            override suspend fun get(id: String) = staged[id]
            override suspend fun put(payment: PendingPayment) { staged[payment.id] = payment }
        }
        val result = tx.block()
        records.clear()
        records.addAll(stagedRecords)
        candidates.clear()
        candidates.putAll(staged)
        result
    }
}
