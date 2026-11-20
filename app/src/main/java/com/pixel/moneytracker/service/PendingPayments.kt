package com.pixel.moneytracker.service

import android.content.Context
import com.pixel.moneytracker.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Both UIs use this transaction boundary; UI cleanup happens only after commit. */
object PendingPayments {
    fun store(context: Context) = PendingPaymentStore(RoomPendingPaymentBackend(AppDatabase.getDatabase(context)))

    /** Package replacement can clear notifications without consuming persisted candidates. */
    suspend fun restoreNotifications(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(android.app.NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        val existing = manager.activeNotifications.filter {
            it.notification.channelId == PendingPaymentNotifications.CHANNEL_ID
        }.mapNotNull { it.tag }.toSet()
        val candidates = AppDatabase.getDatabase(app).pendingPaymentDao().available(System.currentTimeMillis())
        for (candidate in candidates) {
            if (candidate.id !in existing) PendingPaymentNotifications(app).post(candidate)
        }
    }

    suspend fun resolve(context: Context, id: String, edit: PaymentEdit?): PaymentResolution {
        val store = store(context)
        val result = if (edit == null) store.ignore(id) else store.confirm(id, edit)
        withContext(Dispatchers.Main.immediate) {
            PendingPaymentNotifications(context).cancel(id)
            FloatingWindowService.detachCandidate(id)
        }
        return result
    }
}
