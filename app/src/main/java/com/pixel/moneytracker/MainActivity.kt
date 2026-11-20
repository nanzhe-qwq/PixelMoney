package com.pixel.moneytracker

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Bundle
import android.content.Intent
import com.pixel.moneytracker.reminder.BudgetAlert
import com.pixel.moneytracker.reminder.DailyReminder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pixel.moneytracker.data.entity.Record
import com.pixel.moneytracker.service.KeepAliveService
import com.pixel.moneytracker.ui.screen.*
import com.pixel.moneytracker.ui.theme.*
import com.pixel.moneytracker.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private var reminderRequested by mutableStateOf(false)
    private var budgetRequested by mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == DailyReminder.OPEN_RECORD) reminderRequested = true
        if (intent.action == BudgetAlert.OPEN_BUDGET) budgetRequested = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reminderRequested = intent.action == DailyReminder.OPEN_RECORD
        budgetRequested = intent.action == BudgetAlert.OPEN_BUDGET
        DailyReminder.scheduleIfEnabled(this)

        // 自适应刷新率
        window?.let { win ->
            val params = win.attributes
            params.preferredDisplayModeId = 0
            win.attributes = params
        }

        // 启动保活服务
        KeepAliveService.start(this)

        // 检查是否首次打开
        val prefs = getSharedPreferences("pixel_money", MODE_PRIVATE)
        val hasSeenGuide = prefs.getBoolean("has_seen_guide", false)

        setContent {
            PixelMoneyTheme {
                if (hasSeenGuide) {
                    MainApp(
                        reminderRequested = reminderRequested,
                        budgetRequested = budgetRequested,
                        onReminderHandled = {
                            reminderRequested = false
                            intent.action = null
                        },
                        onBudgetHandled = {
                            budgetRequested = false
                            intent.action = null
                        }
                    )
                } else {
                    PermissionGuideScreen(
                        onDone = {
                            prefs.edit().putBoolean("has_seen_guide", true).apply()
                            // 重启 Activity 进入主页
                            recreate()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 全屏页面（不显示底部导航栏）
 */
private enum class FullScreenPage {
    ADD_RECORD,
    RECURRING,
    STATS,
    LEDGER,
    PROFILE,
    SETTINGS,
    PAYMENT,
    BUDGET,
    TRASH
}

/**
 * 底部导航 Tab（只有首页是 Tab 切换）
 */
sealed class BottomTab(val title: String, val icon: ImageVector) {
    object Home : BottomTab("首页", Icons.Default.Home)
    object Stats : BottomTab("统计", Icons.Default.BarChart)
    object Ledger : BottomTab("账本", Icons.AutoMirrored.Filled.MenuBook)
    object Profile : BottomTab("我的", Icons.Default.Person)
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainApp(
    reminderRequested: Boolean = false,
    budgetRequested: Boolean = false,
    onReminderHandled: () -> Unit = {},
    onBudgetHandled: () -> Unit = {}
) {
    val viewModel: MainViewModel = viewModel()
    var fullPage by remember { mutableStateOf<FullScreenPage?>(null) }
    var editingRecord by remember { mutableStateOf<Record?>(null) }
    // 追踪导航方向：true=返回（从左到右），false=前进（从右到左）
    var isGoingBack by remember { mutableStateOf(false) }
    LaunchedEffect(reminderRequested) {
        if (reminderRequested) {
            if (fullPage != FullScreenPage.ADD_RECORD) {
                val today = java.time.LocalDate.now()
                viewModel.setMonth(today.year, today.monthValue)
                viewModel.setSelectedDay(today.dayOfMonth)
                isGoingBack = false
                editingRecord = null
                fullPage = FullScreenPage.ADD_RECORD
            }
            onReminderHandled()
        }
    }
    // 点预算通知 → 直接进「月度预算」页
    LaunchedEffect(budgetRequested) {
        if (budgetRequested) {
            val today = java.time.LocalDate.now()
            viewModel.setMonth(today.year, today.monthValue)
            isGoingBack = false
            editingRecord = null
            fullPage = FullScreenPage.BUDGET
            onBudgetHandled()
        }
    }

    // ====== 双击返回退出 ======
    var showExitHint by remember { mutableStateOf(false) }
    var exitHintTime by remember { mutableStateOf(0L) }

    // 主页返回：第一次弹提示，第二次退出
    val context = androidx.compose.ui.platform.LocalContext.current
    BackHandler(enabled = fullPage == null) {
        val now = System.currentTimeMillis()
        if (showExitHint && now - exitHintTime < 2000) {
            // 2 秒内再按一次 → 退出
            (context as? android.app.Activity)?.finish()
        } else {
            // 第一次按 → 显示提示
            showExitHint = true
            exitHintTime = now
        }
    }

    // 2 秒后自动隐藏提示
    LaunchedEffect(showExitHint) {
        if (showExitHint) {
            kotlinx.coroutines.delay(2000)
            showExitHint = false
        }
    }

    // ====== 全屏页面返回 ======
    BackHandler(enabled = fullPage != null) {
        isGoingBack = true
        when (fullPage) {
            FullScreenPage.SETTINGS -> fullPage = FullScreenPage.PROFILE
            FullScreenPage.PAYMENT -> fullPage = FullScreenPage.PROFILE
            FullScreenPage.RECURRING -> fullPage = FullScreenPage.PROFILE
            FullScreenPage.BUDGET -> fullPage = FullScreenPage.PROFILE
            FullScreenPage.TRASH -> fullPage = FullScreenPage.PROFILE
            FullScreenPage.PROFILE -> fullPage = null
            FullScreenPage.STATS -> fullPage = null
            FullScreenPage.LEDGER -> fullPage = null
            FullScreenPage.ADD_RECORD -> {
                fullPage = null
                editingRecord = null
            }
            null -> {}
        }
    }

    // 快速 tween 动画（比 spring 流畅）
    val fastTween = tween<IntOffset>(durationMillis = 250, easing = FastOutSlowInEasing)

    Box(modifier = Modifier.fillMaxSize()) {
    AnimatedContent(
        targetState = fullPage,
        transitionSpec = {
            if (targetState == FullScreenPage.ADD_RECORD && !isGoingBack) {
                // 添加记录：从下往上
                slideInVertically(fastTween) { it } + fadeIn(tween(200)) togetherWith
                    slideOutVertically(fastTween) { -it / 4 } + fadeOut(tween(200))
            } else if (isGoingBack) {
                // 返回：从左到右
                slideInHorizontally(fastTween) { -it / 3 } + fadeIn(tween(200)) togetherWith
                    slideOutHorizontally(fastTween) { it / 3 } + fadeOut(tween(200))
            } else {
                // 前进：从右到左
                slideInHorizontally(fastTween) { it / 3 } + fadeIn(tween(200)) togetherWith
                    slideOutHorizontally(fastTween) { -it / 3 } + fadeOut(tween(200))
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(PixelDarkBg),
        label = "screen_transition"
    ) { page ->
        when (page) {
            FullScreenPage.ADD_RECORD -> {
                AddRecordScreen(
                    viewModel = viewModel,
                    editRecord = editingRecord,
                    onDone = {
                        fullPage = null
                        editingRecord = null
                    }
                )
            }
            FullScreenPage.RECURRING -> {
                RecurringScreen(
                    viewModel = viewModel,
                    onBack = { isGoingBack = true; fullPage = FullScreenPage.PROFILE }
                )
            }
            FullScreenPage.STATS -> {
                StatsScreen(
                    viewModel = viewModel,
                    onBack = { isGoingBack = true; fullPage = null }
                )
            }
            FullScreenPage.LEDGER -> {
                LedgerScreen(
                    viewModel = viewModel,
                    onBack = { isGoingBack = true; fullPage = null }
                )
            }
            FullScreenPage.PROFILE -> {
                ProfileScreen(
                    viewModel = viewModel,
                    onBack = { isGoingBack = true; fullPage = null },
                    onNavigateToSettings = { isGoingBack = false; fullPage = FullScreenPage.SETTINGS },
                    onNavigateToRecurring = { isGoingBack = false; fullPage = FullScreenPage.RECURRING },
                    onNavigateToPayment = { isGoingBack = false; fullPage = FullScreenPage.PAYMENT },
                    onNavigateToBudget = { isGoingBack = false; fullPage = FullScreenPage.BUDGET },
                    onNavigateToTrash = { isGoingBack = false; fullPage = FullScreenPage.TRASH }
                )
            }
            FullScreenPage.SETTINGS -> {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { isGoingBack = true; fullPage = FullScreenPage.PROFILE }
                )
            }
            FullScreenPage.PAYMENT -> {
                PaymentScreen(
                    onBack = { isGoingBack = true; fullPage = FullScreenPage.PROFILE }
                )
            }
            FullScreenPage.BUDGET -> {
                BudgetScreen(
                    viewModel = viewModel,
                    onBack = { isGoingBack = true; fullPage = FullScreenPage.PROFILE }
                )
            }
            FullScreenPage.TRASH -> {
                TrashScreen(
                    viewModel = viewModel,
                    onBack = { isGoingBack = true; fullPage = FullScreenPage.PROFILE }
                )
            }
            null -> {
                MainContent(
                    viewModel = viewModel,
                    onNavigateToStats = { isGoingBack = false; fullPage = FullScreenPage.STATS },
                    onNavigateToLedger = { isGoingBack = false; fullPage = FullScreenPage.LEDGER },
                    onNavigateToProfile = { isGoingBack = false; fullPage = FullScreenPage.PROFILE },
                    onAddRecord = {
                        isGoingBack = false
                        editingRecord = null
                        fullPage = FullScreenPage.ADD_RECORD
                    },
                    onEditRecord = { record ->
                        isGoingBack = false
                        editingRecord = record
                        fullPage = FullScreenPage.ADD_RECORD
                    }
                )
            }
        }
    }

    // ====== 退出提示弹窗（从顶部滑入，2秒后消失） ======
    AnimatedVisibility(
        visible = showExitHint,
        enter = slideInVertically(tween(200)) { -it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(300)) { -it } + fadeOut(tween(300)),
        modifier = Modifier.align(Alignment.TopCenter)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 48.dp, start = 32.dp, end = 32.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PixelCardBg.copy(alpha = 0.95f))
                .border(1.dp, PixelGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "再滑一次退出应用",
                color = PixelGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
    } // Box
}

@Composable
private fun MainContent(
    viewModel: MainViewModel,
    onNavigateToStats: () -> Unit,
    onNavigateToLedger: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onAddRecord: () -> Unit,
    onEditRecord: (Record) -> Unit
) {

    Scaffold(
        containerColor = PixelDarkBg,
        bottomBar = {
            // ====== 底部导航栏 ======
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PixelCardBg)
                    .border(1.dp, PixelSurface, RoundedCornerShape(0.dp))
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 首页
                BottomTabItem(
                    tab = BottomTab.Home,
                    selected = true,
                    onClick = { /* 已在首页 */ }
                )
                // 统计
                BottomTabItem(
                    tab = BottomTab.Stats,
                    selected = false,
                    onClick = onNavigateToStats
                )

                // ====== 中间 + 按钮（突出设计） ======
                CenterAddButton(onClick = onAddRecord)

                // 账本
                BottomTabItem(
                    tab = BottomTab.Ledger,
                    selected = false,
                    onClick = onNavigateToLedger
                )
                // 我的
                BottomTabItem(
                    tab = BottomTab.Profile,
                    selected = false,
                    onClick = onNavigateToProfile
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            HomeScreen(
                viewModel = viewModel,
                onEditRecord = onEditRecord
            )
        }
    }
}

/**
 * 底栏中间的 + 按钮（圆形、凸起、金色渐变）
 */
@Composable
private fun CenterAddButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "center_btn_scale"
    )

    Box(
        modifier = Modifier
            .size(52.dp)
            .scale(scale)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(PixelGold)
            .border(3.dp, PixelGold.copy(alpha = 0.4f), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = "记一笔",
            tint = PixelDarkBg,
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * 底部导航普通 Tab 项
 */
@Composable
private fun BottomTabItem(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "tab_scale"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) PixelGold else PixelTextDark,
        animationSpec = tween(200),
        label = "tab_color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Icon(
            tab.icon,
            tab.title,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            tab.title,
            color = iconColor,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = 1.sp
        )
    }
}
