package com.pixel.moneytracker.service

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PendingPaymentStoreTest {
    @Test fun expiredProcessedAndUnknownIdsNeverReplayOrMatchOnlyByAmount() = runBlocking {
        val backend = MemoryPendingPaymentBackend()
        var now = 1000L
        val store = PendingPaymentStore(backend) { now }
        val candidate = store.create(12.34, "支付宝", "商户", false)
        val second = store.create(12.34, "支付宝", "商户", false)
        val edit = PaymentEdit("餐饮", "", "", false)
        assertEquals(PaymentResolution.APPLIED, store.ignore(second.id))
        assertNull(store.load(second.id))
        assertNotNull(store.load(candidate.id))
        now = candidate.expiresAt
        assertNull(store.load(candidate.id))
        val restarted = PendingPaymentStore(backend) { now }
        for (id in listOf(candidate.id, second.id, "unknown", "12.34")) {
            assertNull(restarted.load(id))
            assertEquals(PaymentResolution.UNAVAILABLE, restarted.confirm(id, edit))
            assertEquals(PaymentResolution.UNAVAILABLE, restarted.ignore(id))
        }
        assertTrue(backend.records.isEmpty())
    }

    @Test fun confirmAndIgnoreAreAtomicAcrossConcurrentEntrances() = runBlocking {
        val backend = MemoryPendingPaymentBackend()
        val store = PendingPaymentStore(backend) { 1000L }
        val candidate = store.create(12.34, "支付宝", "商户", true)
        val edit = PaymentEdit("工资", "💰", "修改备注", false)
        val results = kotlinx.coroutines.coroutineScope {
            (1..20).map { async(kotlinx.coroutines.Dispatchers.Default) {
                if (it % 2 == 0) store.confirm(candidate.id, edit) else store.ignore(candidate.id)
            } }.map { it.await() }
        }
        assertEquals(1, results.count { it == PaymentResolution.APPLIED })
        assertTrue(backend.records.size <= 1)
        assertEquals(PaymentResolution.UNAVAILABLE, store.confirm(candidate.id, edit))
        assertEquals(PaymentResolution.UNAVAILABLE, store.ignore(candidate.id))
        val other = store.create(12.34, "支付宝", "商户", true)
        assertEquals(PaymentResolution.APPLIED, store.confirm(other.id, edit))
        assertEquals(edit, backend.records.last().second)
        assertEquals(other, backend.records.last().first)
    }

    @Test fun failedSaveRollsBackAndCandidateCanBeRetried() = runBlocking {
        val backend = MemoryPendingPaymentBackend()
        val store = PendingPaymentStore(backend) { 1000L }
        val candidate = store.create(12.34, "支付宝", "商户", false)
        backend.failAfterRecordWrite = true
        val edit = PaymentEdit("餐饮", "", "重试", true)
        try { store.confirm(candidate.id, edit); fail("Expected save error") }
        catch (_: IllegalStateException) { }
        assertTrue(backend.records.isEmpty())
        assertEquals(candidate, store.load(candidate.id))
        backend.failAfterRecordWrite = false
        assertEquals(PaymentResolution.APPLIED, store.confirm(candidate.id, edit))
        assertEquals(1, backend.records.size)
        assertEquals(PaymentResolution.UNAVAILABLE, PendingPaymentStore(backend) { 1000L }.confirm(candidate.id, edit))
    }

    @Test fun validCandidatesPersistAcrossStoreInstancesWithDistinctOpaqueIds() = runBlocking {
        val backend = MemoryPendingPaymentBackend()
        val store = PendingPaymentStore(backend) { 1000L }
        val first = store.create(12.34, "支付宝", "商户", true)
        val second = store.create(12.34, "支付宝", "商户", true)
        assertNotEquals(first.id, second.id)
        assertTrue(first.id.matches(Regex("[a-f0-9-]{36}")))
        assertEquals(first, PendingPaymentStore(backend) { 1000L }.load(first.id))
        assertEquals(second, store.load(second.id))
        assertTrue(first.isIncome)
        for (amount in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            try { store.create(amount, "支付宝", "", false); fail("Invalid amount accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
