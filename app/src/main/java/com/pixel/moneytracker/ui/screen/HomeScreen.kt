package com.pixel.moneytracker.ui.screen

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.ui.window.Dialog
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.moneytracker.data.entity.Record
import com.pixel.moneytracker.data.entity.RecordType
import com.pixel.moneytracker.ui.component.PixelCard
import com.pixel.moneytracker.ui.component.PixelDivider
import com.pixel.moneytracker.ui.component.CategoryIcons
import com.pixel.moneytracker.ui.theme.*
import com.pixel.moneytracker.ui.viewmodel.MainViewModel
import com.pixel.moneytracker.util.DateUtil
import com.pixel.moneytracker.util.Money

/**
 * 金额数字 — 等宽字体 + 右对齐，数值从旧到新平滑滚动
 */
@Composable
private fun AnimatedAmount(
    value: Double,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight = FontWeight.Bold
) {
    val animatedValue by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "amount"
    )
    Text(
        text = DateUtil.formatAmount(animatedValue.toDouble()),
        color = color,
        fontWeight = fontWeight,
        fontSize = fontSize,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        textAlign = TextAlign.End,
        maxLines = 1
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onEditRecord: (Record) -> Unit = {}
) {
    val context = LocalContext.current
    val year by viewModel.selectedYear.collectAsStateWithLifecycle()
    val month by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val income by viewModel.monthIncome.collectAsStateWithLifecycle()
    val expense by viewModel.monthExpense.collectAsStateWithLifecycle()
    val records by viewModel.monthRecords.collectAsStateWithLifecycle()
    val budget by viewModel.budget.collectAsStateWithLifecycle()
    val dailyData by viewModel.dailyExpense.collectAsStateWithLifecycle()

    // 选中的日期 — 唯一数据源在 ViewModel，导航回来不会丢失
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()

    val balance = income - expense
    val budgetAmount = budget?.amount ?: 0.0

    // 记录筛选与按日分组：只在数据变化时重算，避免每次重组都 groupBy
    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf("全部") }
    val displayRecords = remember(records, selectedDay, query, typeFilter) {
        val keyword = query.trim()
        records.filter { record ->
            (selectedDay == null || record.day == selectedDay) &&
                (typeFilter == "全部" || record.type == if (typeFilter == "收入") RecordType.INCOME else RecordType.EXPENSE) &&
                (keyword.isEmpty() || record.note.contains(keyword, true) || record.category.contains(keyword, true) ||
                    DateUtil.formatAmount(record.amount).contains(keyword))
        }
    }
    val groupedRecords = remember(displayRecords) {
        displayRecords.groupBy { it.day }.toSortedMap(compareByDescending { it })
    }
    val budgetUsed = if (budgetAmount > 0) (expense / budgetAmount).coerceIn(0.0, 1.0) else 0.0

    // 预算进度条动画
    val animatedBudgetUsed by animateFloatAsState(
        targetValue = budgetUsed.toFloat(),
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "budget_bar"
    )

    // 删除确认弹窗
    var showDeleteDialog by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<Record?>(null) }
    // 删除后 4 秒内的撤销入口：手滑点掉一笔账不用跑去回收站翻
    var undoRecord by remember { mutableStateOf<Record?>(null) }
    LaunchedEffect(undoRecord) {
        val pending = undoRecord ?: return@LaunchedEffect
        delay(4000)
        if (undoRecord === pending) undoRecord = null
    }

    Box(modifier = Modifier.fillMaxSize().background(PixelDarkBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ====== 月份选择器 ======
            item(key = "month_picker", contentType = "month_picker") {
                MonthPicker(
                    year = year,
                    month = month,
                    onPrev = { viewModel.prevMonth() },
                    onChooseDate = {
                        android.app.DatePickerDialog(context, { _, y, m, d ->
                            viewModel.setMonth(y, m + 1)
                            viewModel.setSelectedDay(d)
                            query = ""
                            typeFilter = "全部"
                        }, year, month - 1, selectedDay ?: 1).show()
                    },
                    onNext = { viewModel.nextMonth() }
                )
            }

            // ====== 日历视图 ======
            item(contentType = "calendar") {
                CalendarView(
                    year = year,
                    month = month,
                    dailyExpenses = dailyData,
                    selectedDay = selectedDay,
                    onDayClick = { day ->
                        val newDay = if (selectedDay == day) null else day
                        viewModel.setSelectedDay(newDay)
                    }
                )
            }

            // ====== 收支总览卡片 ======
            item(key = "summary_card", contentType = "summary_card") {
                val summaryInteraction = remember { MutableInteractionSource() }
                val summaryPressed by summaryInteraction.collectIsPressedAsState()
                val summaryScale by animateFloatAsState(
                    targetValue = if (summaryPressed) 0.98f else 1f,
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
                    label = "summary_scale"
                )
                PixelCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(summaryScale)
                        .clickable(
                            interactionSource = summaryInteraction,
                            indication = null
                        ) {},
                    borderColor = PixelGold,
                    borderWidth = 2.dp
                ) {
                    // 收入
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("收入", color = PixelTextGray, fontSize = 13.sp)
                        AnimatedAmount(income, PixelGreen, 18.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // 支出
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("支出", color = PixelTextGray, fontSize = 13.sp)
                        AnimatedAmount(expense, PixelRed, 18.sp)
                    }

                    PixelDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // 结余
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text("结余", color = PixelGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        AnimatedAmount(
                            balance,
                            if (balance >= 0) PixelGreen else PixelRed,
                            22.sp
                        )
                    }
                }
            }

            // ====== 预算进度条 ======
            item(key = "budget_bar", contentType = "budget_bar") {
                PixelCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("月预算", color = PixelTextGray, fontSize = 13.sp)
                        Text(
                            if (budgetAmount > 0) "${DateUtil.formatAmount(expense)} / ${DateUtil.formatAmount(budgetAmount)}"
                            else "未设置",
                            color = PixelTextGray,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // 像素进度条
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .border(1.dp, PixelSurface, RoundedCornerShape(2.dp))
                            .background(PixelDarkBg)
                    ) {
                        val barColor = when {
                            animatedBudgetUsed < 0.6f -> PixelGreen
                            animatedBudgetUsed < 0.85f -> PixelOrange
                            else -> PixelRed
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = animatedBudgetUsed.coerceIn(0f, 1f))
                                .background(barColor)
                        )
                    }

                    if (budgetAmount > 0) {
                        // 金额统一折算成分再比较：避免浮点在「剩余 0」和 80% 边界上抖动
                        val limitCents = Money.toCents(budgetAmount)
                        val spentCents = Money.toCents(expense)
                        val remainingCents = limitCents - spentCents
                        val daysInMonth = DateUtil.daysInMonth(year, month)
                        val daysLeft = (daysInMonth - DateUtil.currentDay() + 1).coerceAtLeast(1)
                        val isCurrentMonth = year == DateUtil.currentYear() && month == DateUtil.currentMonth()

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "已用 ${(animatedBudgetUsed * 100).toInt()}%",
                                color = if (animatedBudgetUsed < 0.85f) PixelTextGray else PixelRed,
                                fontSize = 11.sp
                            )
                            // 剩余金额：刚好为 0 时属于「用完」而不是「超支」
                            Text(
                                if (remainingCents >= 0) "剩余 ¥${Money.format(remainingCents)}"
                                else "已超支 ¥${Money.format(-remainingCents)}",
                                color = if (remainingCents >= 0) PixelGreen else PixelRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (isCurrentMonth && remainingCents >= 0) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "每日可用 ${DateUtil.formatAmount(remainingCents / 100.0 / daysLeft)}",
                                color = PixelTextGray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            item(key = "search") {
                OutlinedTextField(
                    value = query, onValueChange = { query = it; viewModel.setSelectedDay(null) },
                    label = { Text("搜索本月备注、分类或金额") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PixelTextWhite, unfocusedTextColor = PixelTextWhite,
                        focusedBorderColor = PixelGold, unfocusedBorderColor = PixelTextGray,
                        focusedLabelColor = PixelGold, unfocusedLabelColor = PixelTextGray
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("全部", "支出", "收入").forEach { type ->
                        FilterChip(selected = typeFilter == type, onClick = { typeFilter = type }, label = { Text(type) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PixelGold, selectedLabelColor = PixelDarkBg))
                    }
                }
            }
            // ====== 记录列表标题 ======
            item(key = "list_header", contentType = "list_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val filteredRecords = displayRecords

                    if (selectedDay != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = PixelGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${month}月${selectedDay}日",
                                color = PixelGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // 取消筛选按钮
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(PixelSurface)
                                    .clickable { viewModel.setSelectedDay(null) }
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("全部", color = PixelTextGray, fontSize = 11.sp)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Receipt,
                                contentDescription = null,
                                tint = PixelGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "交易记录",
                                color = PixelGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    Text(
                        "共 ${filteredRecords.size} 笔",
                        color = PixelTextDark,
                        fontSize = 12.sp
                    )
                }
                if (displayRecords.isNotEmpty() && (selectedDay != null || query.isNotBlank() || typeFilter != "全部")) {
                    val subtotal = remember(displayRecords) {
                        displayRecords.sumOf { if (it.type == RecordType.INCOME) it.amount else 0.0 } to
                            displayRecords.sumOf { if (it.type == RecordType.EXPENSE) it.amount else 0.0 }
                    }
                    Text("小计   收入 ${DateUtil.formatAmount(subtotal.first)}   支出 ${DateUtil.formatAmount(subtotal.second)}",
                        color = PixelTextGray, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }

            // ====== 记录列表 ======
            if (displayRecords.isEmpty()) {
                item(key = "empty_hint", contentType = "empty_hint") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (query.isNotBlank() || typeFilter != "全部") "没有匹配的记录\n试试其他关键词或收支类型" else "还没有记录\n点击底栏中间 + 开始记账",
                            color = PixelTextDark,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                groupedRecords.forEach { (day, dayRecords) ->
                    item(key = "day_header_$day", contentType = "day_header") {
                        Text(
                            text = "${month}月${day}日",
                            color = PixelTextGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(
                        dayRecords,
                        key = { it.id },
                        contentType = { "record_item" }
                    ) { record ->
                        RecordItem(
                            record = record,
                            onEdit = { onEditRecord(record) },
                            onDelete = {
                                recordToDelete = record
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }

            // 底部留白
            item(key = "spacer", contentType = "spacer") { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // ====== 撤销条（删除后从底部滑入，4 秒后自动消失） ======
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
    AnimatedVisibility(
        visible = undoRecord != null,
        enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(220)),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        val pending = undoRecord
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(PixelCardBg)
                .border(1.dp, PixelSurface, RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "已移到回收站",
                color = PixelTextGray,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    pending?.let { viewModel.restoreRecord(it.id) }
                    undoRecord = null
                }
            ) {
                Text("撤销", color = PixelGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
    }

    // ====== 删除确认弹窗 ======
    if (showDeleteDialog && recordToDelete != null) {
        DeleteConfirmDialog(
            record = recordToDelete!!,
            onConfirm = {
                recordToDelete?.let { target ->
                    viewModel.deleteRecord(target) { moved ->
                        if (moved) undoRecord = target
                    }
                }
                showDeleteDialog = false
                recordToDelete = null
            },
            onDismiss = {
                showDeleteDialog = false
                recordToDelete = null
            }
        )
    }
}

@Composable
private fun MonthPicker(
    year: Int,
    month: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onChooseDate: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左箭头带按下缩放
        val leftInteraction = remember { MutableInteractionSource() }
        val leftPressed by leftInteraction.collectIsPressedAsState()
        val leftScale by animateFloatAsState(
            targetValue = if (leftPressed) 0.8f else 1f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
            label = "left_arrow"
        )
        IconButton(
            onClick = onPrev,
            interactionSource = leftInteraction,
            modifier = Modifier.scale(leftScale)
        ) {
            Icon(Icons.Default.ChevronLeft, "上月", tint = PixelGold)
        }

    // 月份文字切换动画
    val monthTween = tween<IntOffset>(durationMillis = 200, easing = FastOutSlowInEasing)
    AnimatedContent(
        targetState = "${year}年 ${DateUtil.monthName(month)}",
        transitionSpec = {
            if (targetState > initialState) {
                slideInHorizontally(monthTween) { it } togetherWith
                    slideOutHorizontally(monthTween) { -it }
            } else {
                slideInHorizontally(monthTween) { -it } togetherWith
                    slideOutHorizontally(monthTween) { it }
            }
        },
        label = "month_text"
    ) { text ->
            TextButton(onClick = onChooseDate) {
                Icon(Icons.Default.CalendarToday, contentDescription = "跳到指定日期", modifier = Modifier.size(16.dp))
                Text(
                text = text,
                style = PixelTitleStyle,
                modifier = Modifier.padding(start = 6.dp)
                )
            }
        }

        // 右箭头带按下缩放
        val rightInteraction = remember { MutableInteractionSource() }
        val rightPressed by rightInteraction.collectIsPressedAsState()
        val rightScale by animateFloatAsState(
            targetValue = if (rightPressed) 0.8f else 1f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
            label = "right_arrow"
        )
        IconButton(
            onClick = onNext,
            interactionSource = rightInteraction,
            modifier = Modifier.scale(rightScale)
        ) {
            Icon(Icons.Default.ChevronRight, "下月", tint = PixelGold)
        }
    }
}

/**
 * 日历视图 — 显示当月每天的支出金额，支持点击选中
 */
@Composable
private fun CalendarView(
    year: Int,
    month: Int,
    dailyExpenses: List<com.pixel.moneytracker.data.dao.DailySummary>,
    selectedDay: Int? = null,
    onDayClick: (Int) -> Unit = {}
) {
    val expenseMap = remember(dailyExpenses) { dailyExpenses.associate { it.day to it.total } }

    val calData = remember(year, month) {
        val cal = java.util.Calendar.getInstance().apply { set(year, month - 1, 1) }
        val first = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val days = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        Pair(first, days)
    }
    val firstDayOfWeek = calData.first
    val daysInMonth = calData.second

    val todayData = remember {
        val today = java.util.Calendar.getInstance()
        Triple(today.get(java.util.Calendar.YEAR), today.get(java.util.Calendar.MONTH) + 1, today.get(java.util.Calendar.DAY_OF_MONTH))
    }
    val isCurrentMonth = todayData.first == year && todayData.second == month
    val todayDay = todayData.third

    PixelCard(modifier = Modifier.fillMaxWidth()) {
        // 星期标题行
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { day ->
                Text(
                    day,
                    color = if (day == "日" || day == "六") PixelRed.copy(alpha = 0.6f) else PixelTextDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 日期网格
        val totalCells = firstDayOfWeek - 1 + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            ) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val day = cellIndex - (firstDayOfWeek - 1) + 1

                    if (day in 1..daysInMonth) {
                        val isToday = isCurrentMonth && day == todayDay
                        val isSelected = day == selectedDay
                        val isFuture = (year > todayData.first) ||
                            (year == todayData.first && month > todayData.second) ||
                            (isCurrentMonth && day > todayDay)
                        val dayExpense = expenseMap[day] ?: 0.0
                        val hasExpense = dayExpense > 0

                        // 背景色优先级：选中 > 今天 > 有支出 > 透明
                        val bgColor = when {
                            isFuture -> androidx.compose.ui.graphics.Color.Transparent
                            isSelected -> PixelGold.copy(alpha = 0.25f)
                            isToday -> PixelGold.copy(alpha = 0.1f)
                            else -> androidx.compose.ui.graphics.Color.Transparent
                        }
                        val borderColor = when {
                            isFuture -> androidx.compose.ui.graphics.Color.Transparent
                            isSelected -> PixelGold
                            isToday -> PixelGold.copy(alpha = 0.4f)
                            else -> androidx.compose.ui.graphics.Color.Transparent
                        }
                        val dayColor = when {
                            isFuture -> PixelTextDark
                            isSelected -> PixelGold
                            isToday -> PixelGold
                            else -> PixelTextWhite
                        }
                        val expenseColor = when {
                            isFuture -> PixelTextDark
                            isSelected -> PixelGold.copy(alpha = 0.8f)
                            else -> PixelRed.copy(alpha = 0.8f)
                        }

                        // 日历格子点击动画
                        val dayInteraction = remember { MutableInteractionSource() }
                        val dayPressed by dayInteraction.collectIsPressedAsState()
                        val dayScale by animateFloatAsState(
                            targetValue = if (dayPressed) 0.9f else 1f,
                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
                            label = "day_scale"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .scale(dayScale)
                                .clip(RoundedCornerShape(2.dp))
                                .then(
                                    if (borderColor != androidx.compose.ui.graphics.Color.Transparent)
                                        Modifier.border(1.dp, borderColor, RoundedCornerShape(2.dp))
                                    else Modifier
                                )
                                .background(bgColor)
                                .then(
                                    if (isFuture) Modifier
                                    else Modifier.clickable(
                                        interactionSource = dayInteraction,
                                        indication = null
                                    ) { onDayClick(day) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    "$day",
                                    color = dayColor,
                                    fontSize = 14.sp,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.wrapContentSize(Alignment.Center)
                                )
                                if (hasExpense) {
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        DateUtil.formatAmountCompact(dayExpense),
                                        color = expenseColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier.wrapContentSize(Alignment.Center)
                                    )
                                } else {
                                    // 无支出时显示小圆点保持行高一致
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text("·", color = PixelTextDark, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    record: Record,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sign = if (record.type == RecordType.EXPENSE) "-" else "+"
    val categoryIcon = CategoryIcons.getByName(record.category)

    // 像素风弹窗
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PixelCardBg)
                .border(2.dp, PixelRed.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 标题
                Text(
                    "移到回收站？",
                    color = PixelRed,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 分割线
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(PixelSurface)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 记录信息
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(PixelSurface.copy(alpha = 0.3f))
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = categoryIcon.icon,
                        contentDescription = record.category,
                        tint = categoryIcon.color,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            record.category,
                            color = PixelTextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        if (record.note.isNotBlank()) {
                            Text(
                                record.note,
                                color = PixelTextDark,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "$sign${DateUtil.formatAmount(record.amount)}",
                        color = if (record.type == RecordType.EXPENSE) PixelRed else PixelGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "删除后 30 天内都能从「回收站」找回，也可以点撤销。",
                    color = PixelTextGray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 取消按钮
                    val cancelInteraction = remember { MutableInteractionSource() }
                    val cancelPressed by cancelInteraction.collectIsPressedAsState()
                    val cancelScale by animateFloatAsState(
                        targetValue = if (cancelPressed) 0.92f else 1f,
                        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
                        label = "cancel_scale"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .scale(cancelScale)
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, PixelSurface, RoundedCornerShape(4.dp))
                            .background(PixelSurface.copy(alpha = 0.3f))
                            .clickable(
                                interactionSource = cancelInteraction,
                                indication = null
                            ) { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "取消",
                            color = PixelTextGray,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }

                    // 删除按钮
                    val deleteInteraction = remember { MutableInteractionSource() }
                    val deletePressed by deleteInteraction.collectIsPressedAsState()
                    val deleteScale by animateFloatAsState(
                        targetValue = if (deletePressed) 0.92f else 1f,
                        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
                        label = "delete_btn_scale"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .scale(deleteScale)
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, PixelRed, RoundedCornerShape(4.dp))
                            .background(PixelRed.copy(alpha = 0.2f))
                            .clickable(
                                interactionSource = deleteInteraction,
                                indication = null
                            ) { onConfirm() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "移到回收站",
                            color = PixelRed,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordItem(
    record: Record,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val amountColor = if (record.type == RecordType.EXPENSE) PixelRed else PixelGreen
    val sign = if (record.type == RecordType.EXPENSE) "-" else "+"

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 按压缩放动画（跟 ProfileMenuItem 一样流畅）
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label = "record_scale"
    )

    PixelCard(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true)
            ) { onEdit() },
        borderWidth = 1.dp,
        borderColor = if (isPressed) PixelGold.copy(alpha = 0.5f) else PixelSurface.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧图标 + 分类 + 备注
            Row(verticalAlignment = Alignment.CenterVertically) {
                val categoryIcon = CategoryIcons.getByName(record.category)
                Icon(
                    imageVector = categoryIcon.icon,
                    contentDescription = record.category,
                    tint = categoryIcon.color,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = record.category,
                        color = PixelTextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (record.note.isNotBlank()) {
                        Text(
                            text = record.note,
                            color = PixelTextDark,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 右侧金额 + 删除
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$sign${DateUtil.formatAmount(record.amount)}",
                    color = amountColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 像素风删除按钮
                val deleteInteraction = remember { MutableInteractionSource() }
                val deletePressed by deleteInteraction.collectIsPressedAsState()
                val deleteScale by animateFloatAsState(
                    targetValue = if (deletePressed) 0.85f else 1f,
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
                    label = "delete_scale"
                )
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .scale(deleteScale)
                        .clip(RoundedCornerShape(3.dp))
                        .border(1.dp, PixelRed.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                        .background(PixelRed.copy(alpha = 0.15f))
                        .clickable(
                            interactionSource = deleteInteraction,
                            indication = null
                        ) { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        "删除",
                        tint = PixelRed,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}
