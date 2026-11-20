package com.pixel.moneytracker.service

/** Dispatches prompts only; there is deliberately no automatic Activity launcher. */
class PaymentPromptDispatcher(
    private val store: PendingPaymentStore,
    private val postNotification: (PendingPayment) -> Boolean,
    private val showOverlay: (PendingPayment?) -> Unit
) {
    suspend fun dispatch(amount: Double, source: String, note: String, isIncome: Boolean): PendingPayment? {
        if (!source.startsWith("支付宝")) {
            showOverlay(null)
            return null
        }
        val candidate = store.create(amount, source, note, isIncome)
        try { postNotification(candidate) } catch (_: SecurityException) { /* Overlay remains available. */ }
        showOverlay(candidate)
        return candidate
    }
}
