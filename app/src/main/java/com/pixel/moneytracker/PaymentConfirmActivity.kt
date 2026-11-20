package com.pixel.moneytracker

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.pixel.moneytracker.service.*
import com.pixel.moneytracker.ui.theme.PixelMoneyTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Opened only by an explicit user notification click. No overlay permission required. */
class PaymentConfirmActivity : ComponentActivity() {
    private var busy by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = PendingPaymentNotifications.candidateId(intent) ?: return finish()
        // Runs synchronously on main, before a second interactive UI can be composed.
        FloatingWindowService.detachCandidate(id)
        lifecycleScope.launch {
            try {
                val candidate = PendingPayments.store(this@PaymentConfirmActivity).load(id)
                if (candidate == null) {
                    PendingPaymentNotifications(this@PaymentConfirmActivity).cancel(id)
                    finish()
                    return@launch
                }
                setContent {
                    PixelMoneyTheme {
                        BackHandler { if (!busy) finish() } // Keep candidate + notification.
                        PaymentConfirmDialog(
                            amount = candidate.amount,
                            source = candidate.source,
                            initialNote = candidate.note,
                            isIncome = candidate.isIncome,
                            enabled = !busy,
                            onConfirm = { category, emoji, note, income ->
                                resolve(id, PaymentEdit(category, emoji, note, income))
                            },
                            onDismiss = { resolve(id, null) },
                            onCancel = { if (!busy) finish() }
                        )
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                Toast.makeText(this@PaymentConfirmActivity, "暂时无法读取账单，请稍后点击通知重试", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun resolve(id: String, edit: PaymentEdit?) {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            try {
                PendingPayments.resolve(this@PaymentConfirmActivity, id, edit)
                finish()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                // The database transaction rolled back. Keep edits, candidate and notification.
                busy = false
                Toast.makeText(this@PaymentConfirmActivity, "保存失败，请重试", Toast.LENGTH_LONG).show()
            }
        }
    }
}
