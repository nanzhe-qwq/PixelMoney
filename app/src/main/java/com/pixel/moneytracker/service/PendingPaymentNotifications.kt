package com.pixel.moneytracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pixel.moneytracker.PaymentConfirmActivity
import java.util.Locale

class PendingPaymentNotifications(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "pixel_money_alipay_pending"
        private const val NOTIFICATION_ID = 1 // UUID tag is the exact candidate identity.
        fun uri(id: String): Uri = Uri.Builder().scheme("pixelmoney").authority("pending").appendPath(id).build()
        fun candidateId(intent: Intent): String? {
            val uri = intent.data ?: return null
            if (uri.scheme != "pixelmoney" || uri.host != "pending" || uri.pathSegments.size != 1) return null
            return uri.lastPathSegment?.takeIf { it.matches(Regex("[a-f0-9-]{36}")) }
        }
    }

    fun post(candidate: PendingPayment): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "支付宝账单待确认", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "点击通知后打开记账确认页"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        })
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled() ||
            manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val intent = Intent(context, PaymentConfirmActivity::class.java).setData(uri(candidate.id))
        val click = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("像素记账")
            .setContentText("有账单待确认，解锁后查看")
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("支付宝账单待确认")
            .setContentText("¥${String.format(Locale.ROOT, "%.2f", candidate.amount)} · 点击确认")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(click)
            .setAutoCancel(false) // Back/cancel is not an ignore; let the user return later.
            .setTimeoutAfter((candidate.expiresAt - System.currentTimeMillis()).coerceAtLeast(1))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        return try {
            manager.notify(candidate.id, NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) { false }
    }

    fun cancel(id: String) {
        context.getSystemService(NotificationManager::class.java).cancel(id, NOTIFICATION_ID)
    }
}
