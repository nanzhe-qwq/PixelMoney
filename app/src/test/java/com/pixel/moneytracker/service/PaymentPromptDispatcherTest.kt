package com.pixel.moneytracker.service

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PaymentPromptDispatcherTest {
    @Test fun otherSourcesKeepLegacyOverlayWithoutCreatingCandidatesOrNotifications() = runBlocking {
        val store = PendingPaymentStore(MemoryPendingPaymentBackend()) { 1000L }
        var posts = 0
        var overlays = 0
        val dispatch = PaymentPromptDispatcher(store, { posts++; true }, { assertNull(it); overlays++ })
        for (source in listOf("微信", "测试样本")) {
            assertNull(dispatch.dispatch(12.34, source, "", false))
        }
        assertEquals(0, posts)
        assertEquals(2, overlays)
    }

    @Test fun disabledOrDeniedNotificationsRetainTheSameOverlayCandidate() = runBlocking {
        for (denied in listOf(false, true)) {
            val store = PendingPaymentStore(MemoryPendingPaymentBackend()) { 1000L }
            var shown: PendingPayment? = null
            val dispatch = PaymentPromptDispatcher(store, postNotification = {
                if (denied) throw SecurityException("Permission denied")
                false
            }, showOverlay = { shown = it })
            val candidate = dispatch.dispatch(12.34, "支付宝(收入)", "商户", true)
            assertNotNull(candidate)
            assertEquals(candidate, shown)
            assertEquals(candidate, store.load(candidate!!.id))
        }
    }
}
