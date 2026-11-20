package com.pixel.moneytracker.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.room.withTransaction
import com.pixel.moneytracker.data.entity.Ledger
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.doOnPreDraw
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pixel.moneytracker.data.database.AppDatabase
import com.pixel.moneytracker.data.entity.Record
import com.pixel.moneytracker.data.entity.RecordType
import com.pixel.moneytracker.ui.theme.*
import com.pixel.moneytracker.util.Categories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.runtime.saveable.rememberSaveable
import java.util.Calendar

/**
 * 支付确认悬浮窗服务
 */
class FloatingWindowService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CONTENT = "content"
        const val EXTRA_NOTE = "note"
        const val EXTRA_IS_INCOME = "is_income"
        const val EXTRA_TRANSACTION_TIME = "transaction_time"

        private var instance: FloatingWindowService? = null

        fun isActive() = instance != null

        internal fun activityPayment(id: Long): ScreenPayment? = instance?.let {
            it.currentPayment?.takeIf { _ -> it.activityPrompt && it.currentRequestId == id }
        }

        internal fun resolveActivity(id: Long, edit: PaymentEdit?, onSuccess: () -> Unit, onFailure: () -> Unit) {
            val service = instance
            val payment = activityPayment(id)
            if (service == null || payment == null) { onSuccess(); return }
            if (service.saving) return
            if (edit == null) {
                onSuccess()
                service.showNextPayment()
            } else {
                service.saving = true
                service.saveRecord(payment.amount, edit.category, edit.emoji, edit.note, payment.source,
                    edit.isIncome, payment.transactionTime,
                    onSuccess = { onSuccess(); service.showNextPayment() },
                    onFailure = { service.saving = false; onFailure() })
            }
        }

        /** Main-thread only: notification Activity must detach before composing its own controls. */
        fun detachCandidate(id: String) {
            instance?.let { service ->
                if (service.displayedCandidateId == id) service.removeFloatingWindow()
            }
        }

        fun isShowingCandidate(id: String) = instance?.let {
            it.displayedCandidateId == id && it.floatingView != null
        } ?: false
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var currentPayment: ScreenPayment? = null
    private var activityPrompt = false
    private var currentRequestId = System.nanoTime()
    private var saving = false
    // shortcut: this queue survives only the current process; use durable candidates for kill recovery.
    private val queuedPayments = ArrayDeque<Pair<ScreenPayment, PaymentTrace?>>()
    private var displayedCandidateId: String? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        instance = this
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val amount = intent?.getDoubleExtra(EXTRA_AMOUNT, 0.0) ?: 0.0
        val source = intent?.getStringExtra(EXTRA_SOURCE) ?: "未知"
        val note = intent?.getStringExtra(EXTRA_NOTE) ?: ""
        val isIncome = intent?.getBooleanExtra(EXTRA_IS_INCOME, false) ?: false

        val transactionTime = intent?.getLongExtra(EXTRA_TRANSACTION_TIME, 0L)?.takeIf { it > 0 }
        val trace = PaymentTiming.restore(intent)
        PaymentTiming.mark(trace, TraceStage.SERVICE_STARTED)
        if (amount.isFinite() && amount > 0 && PaymentAppSettings.enabled(this)) {
            val restoreActivity = activityPrompt && !com.pixel.moneytracker.PaymentPopupActivity.isVisible()
            val payment = ScreenPayment(source, amount, note, isIncome, transactionTime)
            // Only coalesce an identical visible/queued prompt; distinct merchants still queue.
            if ((payment != currentPayment || (floatingView == null && !activityPrompt)) && queuedPayments.none { it.first == payment }) {
                if (floatingView == null && !activityPrompt) {
                    currentPayment = payment
                    showFloatingWindow(amount, source, note, isIncome, trace, transactionTime = transactionTime)
                } else queuedPayments.addLast(payment to trace)
            }
            if (restoreActivity) {
                try { startActivity(activityIntent(trace)) }
                catch (_: SecurityException) { PaymentTiming.mark(trace, TraceStage.ERROR) }
                catch (_: android.content.ActivityNotFoundException) { PaymentTiming.mark(trace, TraceStage.ERROR) }
            }
        }

        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingWindow(amount: Double, source: String, note: String, isIncome: Boolean = false, trace: PaymentTrace? = null, candidate: PendingPayment? = null, transactionTime: Long? = null) {
        // 移除旧的悬浮窗
        removeFloatingWindow()
        if (candidate == null) {
            currentRequestId++
            try {
                startActivity(activityIntent(trace))
                activityPrompt = true
                return
            } catch (_: android.content.ActivityNotFoundException) {
                PaymentTiming.mark(trace, TraceStage.ERROR)
            } catch (_: SecurityException) {
                PaymentTiming.mark(trace, TraceStage.ERROR)
            }
        }
        // addView is authoritative; a background preflight may reject an otherwise permitted window.
        displayedCandidateId = candidate?.id

        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        val composeView = ComposeView(this).apply {
            // The Compose focus owner must receive focus, not its service-hosted parent.
            descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
            setContent {
                PixelMoneyTheme {
                    var busy by remember { mutableStateOf(false) }
                    PaymentConfirmDialog(
                        amount = amount,
                        source = source,
                        initialNote = note,
                        isIncome = isIncome,
                        transactionTime = transactionTime,
                        enabled = !busy,
                        onConfirm = { category, emoji, finalNote, income ->
                            if (candidate == null) {
                                if (!busy) {
                                    busy = true
                                    saveRecord(amount, category, emoji, finalNote, source, income, transactionTime,
                                        onSuccess = { showNextPayment() }, onFailure = { busy = false })
                                }
                            } else if (!busy && displayedCandidateId == candidate.id) {
                                busy = true
                                resolveCandidate(candidate.id, PaymentEdit(category, emoji, finalNote, income)) { busy = false }
                            }
                        },
                        onDismiss = {
                            if (candidate == null) {
                                showNextPayment()
                            } else if (!busy && displayedCandidateId == candidate.id) {
                                busy = true
                                resolveCandidate(candidate.id, null) { busy = false }
                            }
                        }
                    )
                }
            }
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        if (trace != null) {
            composeView.doOnPreDraw { PaymentTiming.mark(trace, TraceStage.FIRST_PRE_DRAW) }
        }
        floatingView = composeView
        try {
            windowManager.addView(composeView, layoutParams)
            PaymentTiming.mark(trace, TraceStage.VIEW_ATTACHED)
        } catch (_: SecurityException) {
            floatingView = null
            displayedCandidateId = null
            currentPayment = null
            PaymentTiming.mark(trace, TraceStage.ERROR)
        } catch (_: WindowManager.BadTokenException) {
            floatingView = null
            displayedCandidateId = null
            currentPayment = null
            PaymentTiming.mark(trace, TraceStage.ERROR)
        }
    }

    private fun resolveCandidate(id: String, edit: PaymentEdit?, onFailure: () -> Unit) {
        serviceScope.launch {
            try { PendingPayments.resolve(this@FloatingWindowService, id, edit) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                onFailure()
                android.widget.Toast.makeText(this@FloatingWindowService, "保存失败，请重试", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun activityIntent(trace: PaymentTrace?) = Intent(this, com.pixel.moneytracker.PaymentPopupActivity::class.java).apply {
        putExtra(com.pixel.moneytracker.PaymentPopupActivity.EXTRA_REQUEST_ID, currentRequestId)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        PaymentTiming.attach(this, trace)
    }

    private fun removeFloatingWindow() {
        displayedCandidateId = null
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            floatingView = null
        }
    }

    private fun showNextPayment() {
        removeFloatingWindow()
        activityPrompt = false
        saving = false
        currentPayment = null
        if (queuedPayments.isEmpty()) {
            stopSelf()
        } else {
            val (payment, trace) = queuedPayments.removeFirst()
            currentPayment = payment
            showFloatingWindow(payment.amount, payment.source, payment.merchant, payment.isIncome, trace, transactionTime = payment.transactionTime)
        }
    }

    private fun saveRecord(amount: Double, category: String, emoji: String, note: String,
                           source: String, isIncome: Boolean, transactionTime: Long?, onSuccess: () -> Unit, onFailure: () -> Unit) {
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val cal = Calendar.getInstance().apply { transactionTime?.let { timeInMillis = it } }
                db.withTransaction {
                    val ledgerId = db.ledgerDao().getDefault()?.id
                        ?: db.ledgerDao().insert(Ledger(name = "日常开销", emoji = "📝", isDefault = true))
                    db.recordDao().insert(Record(
                        amount = amount,
                        type = if (isIncome) RecordType.INCOME else RecordType.EXPENSE,
                        category = category, emoji = emoji, note = "[$source] $note",
                        year = cal.get(Calendar.YEAR), month = cal.get(Calendar.MONTH) + 1,
                        day = cal.get(Calendar.DAY_OF_MONTH), ledgerId = ledgerId,
                        timestamp = transactionTime ?: System.currentTimeMillis()
                    ))
                }
                onSuccess()
                Toast.makeText(this@FloatingWindowService, "已记账", Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                onFailure()
                Toast.makeText(this@FloatingWindowService, "保存失败，请重试；填写内容已保留", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        removeFloatingWindow()
        com.pixel.moneytracker.PaymentPopupActivity.close()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        instance = null
    }
}

/**
 * 支付确认弹窗 Composable
 */
@Composable
internal fun PaymentConfirmDialog(
    amount: Double,
    source: String,
    initialNote: String = "",
    isIncome: Boolean = false,
    onConfirm: (category: String, emoji: String, note: String, isIncome: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onCancel: () -> Unit = onDismiss,
    enabled: Boolean = true,
    transactionTime: Long? = null
) {
    // 根据收入/支出选择分类列表
    val categories = if (isIncome) Categories.incomeCategories else Categories.expenseCategories
    val suggestedCategory = when {
        source.contains("退款") || initialNote.contains("退款") -> "退款"
        source in listOf("淘宝", "拼多多", "京东") -> "购物"
        source in listOf("美团", "饿了么", "京东外卖") -> "餐饮"
        isIncome -> "其他"
        else -> categories.first().name
    }
    var selectedCategoryName by rememberSaveable { mutableStateOf(suggestedCategory) }
    var noteText by rememberSaveable { mutableStateOf(initialNote) }
    var currentIsIncome by rememberSaveable { mutableStateOf(isIncome) }
    val currentCategories = if (currentIsIncome) Categories.incomeCategories else Categories.expenseCategories
    val selectedCategory = currentCategories.firstOrNull { it.name == selectedCategoryName } ?: currentCategories.first()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(enabled = enabled, onClick = onCancel),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(2.dp, PixelGold, RoundedCornerShape(8.dp))
                .background(PixelCardBg)
                .clickable {} // Consume blank-card taps without disabling descendants.
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题
            Text(
                text = if (currentIsIncome) "💰 检测到收入" else "💰 检测到支付",
                color = PixelGold,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                transactionTime?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(it)) }
                    ?: "记账日期：今天（未读取到交易时间）",
                color = PixelTextGray, fontSize = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            // 来源 + 收支切换
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "来源：$source",
                    color = PixelTextGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                // 收支切换按钮
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button) {
                            currentIsIncome = !currentIsIncome
                            selectedCategoryName = (if (currentIsIncome) Categories.incomeCategories else Categories.expenseCategories).first().name
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (currentIsIncome) "收入" else "支出",
                        color = if (currentIsIncome) PixelGreen else PixelRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, if (currentIsIncome) PixelGreen else PixelRed, RoundedCornerShape(4.dp))
                            .background(if (currentIsIncome) PixelGreen.copy(alpha = 0.2f) else PixelRed.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 金额
            Text(
                text = "¥${String.format("%.2f", amount)}",
                color = if (currentIsIncome) PixelGreen else PixelRed,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 分类选择
            Text("选择分类", color = PixelTextGray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 180.dp)
            ) {
                items(currentCategories, key = { it.name }) { cat ->
                    val catIcon = com.pixel.moneytracker.ui.component.CategoryIcons.getByName(cat.name)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .border(
                                1.dp,
                                if (selectedCategory.name == cat.name) PixelGold else PixelSurface,
                                RoundedCornerShape(4.dp)
                            )
                            .background(
                                if (selectedCategory.name == cat.name) PixelGold.copy(alpha = 0.2f)
                                else PixelDarkBg
                            )
                            .clickable(enabled = enabled) { selectedCategoryName = cat.name }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = catIcon.icon,
                            contentDescription = cat.name,
                            tint = catIcon.color,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = cat.name,
                            fontSize = 10.sp,
                            color = if (selectedCategory.name == cat.name) PixelGold else PixelTextGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 备注输入框
            androidx.compose.material3.OutlinedTextField(
                enabled = enabled,
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("备注（可选）", color = PixelTextGray, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PixelGold,
                    unfocusedBorderColor = PixelSurface,
                    focusedTextColor = PixelTextWhite,
                    unfocusedTextColor = PixelTextWhite,
                    cursorColor = PixelGold,
                ),
                shape = RoundedCornerShape(4.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedButton(
                    enabled = enabled, onClick = onDismiss,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(4.dp)
                ) { Text("忽略", color = PixelTextGray, fontSize = 14.sp) }
                androidx.compose.material3.Button(
                    enabled = enabled,
                    onClick = { onConfirm(selectedCategory.name, selectedCategory.emoji, noteText, currentIsIncome) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = PixelGold, contentColor = PixelDarkBg)
                ) { Text(if (enabled) "记账 ✓" else "保存中…", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
        }
    }
}
