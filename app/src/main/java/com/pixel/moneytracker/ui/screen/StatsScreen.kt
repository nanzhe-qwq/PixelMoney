package com.pixel.moneytracker.ui.screen

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.moneytracker.data.dao.CategorySummary
import com.pixel.moneytracker.data.dao.DailySummary
import com.pixel.moneytracker.ui.component.PixelCard
import com.pixel.moneytracker.ui.component.PixelDivider
import com.pixel.moneytracker.ui.component.CategoryIcons
import com.pixel.moneytracker.ui.theme.*
import com.pixel.moneytracker.ui.viewmodel.MainViewModel
import com.pixel.moneytracker.util.BudgetLevel
import com.pixel.moneytracker.util.BudgetPolicy
import com.pixel.moneytracker.util.DateUtil
import com.pixel.moneytracker.util.Money
import com.pixel.moneytracker.util.YearMonth

@Composable
fun StatsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val year by viewModel.selectedYear.collectAsStateWithLifecycle()
    val month by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val expense by viewModel.monthExpense.collectAsStateWithLifecycle()
    val income by viewModel.monthIncome.collectAsStateWithLifecycle()
    val categories by viewModel.expenseByCategory.collectAsStateWithLifecycle()
    val dailyData by viewModel.dailyExpense.collectAsStateWithLifecycle()
    val budget by viewModel.budget.collectAsStateWithLifecycle()
    val trend by viewModel.expenseTrend.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelDarkBg)
    ) {
        // ====== 顶部栏（左上角返回按钮） ======
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val backInteraction = remember { MutableInteractionSource() }
            val backPressed by backInteraction.collectIsPressedAsState()
            val backScale by animateFloatAsState(
                targetValue = if (backPressed) 0.8f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                ),
                label = "back_scale"
            )

            IconButton(
                onClick = onBack,
                interactionSource = backInteraction,
                modifier = Modifier.scale(backScale)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = PixelGold,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "统计",
                color = PixelGold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ====== 月份选择 ======
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val leftInteraction = remember { MutableInteractionSource() }
                val leftPressed by leftInteraction.collectIsPressedAsState()
                val leftScale by animateFloatAsState(
                    targetValue = if (leftPressed) 0.8f else 1f,
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
                    label = "left_arrow"
                )
                IconButton(
                    onClick = { viewModel.prevMonth() },
                    interactionSource = leftInteraction,
                    modifier = Modifier.scale(leftScale)
                ) {
                    Icon(Icons.Default.ChevronLeft, "上月", tint = PixelGold)
                }
                Text(
                    text = "${year}年 ${DateUtil.monthName(month)}",
                    style = PixelTitleStyle,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                val rightInteraction = remember { MutableInteractionSource() }
                val rightPressed by rightInteraction.collectIsPressedAsState()
                val rightScale by animateFloatAsState(
                    targetValue = if (rightPressed) 0.8f else 1f,
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
                    label = "right_arrow"
                )
                IconButton(
                    onClick = { viewModel.nextMonth() },
                    interactionSource = rightInteraction,
                    modifier = Modifier.scale(rightScale)
                ) {
                    Icon(Icons.Default.ChevronRight, "下月", tint = PixelGold)
                }
            }
        }

        // ====== 收支对比 ======
        item {
            PixelCard(modifier = Modifier.fillMaxWidth()) {
                Text(">> 收支概况", color = PixelGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                val maxVal = maxOf(income, expense, 1.0)

                // 收入条
                Text("收入  ${DateUtil.formatAmount(income)}", color = PixelGreen, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .border(1.dp, PixelSurface, RoundedCornerShape(2.dp))
                        .background(PixelDarkBg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = (income / maxVal).toFloat())
                            .background(PixelGreen)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 支出条
                Text("支出  ${DateUtil.formatAmount(expense)}", color = PixelRed, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .border(1.dp, PixelSurface, RoundedCornerShape(2.dp))
                        .background(PixelDarkBg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = (expense / maxVal).toFloat())
                            .background(PixelRed)
                    )
                }
            }
        }

        // ====== 本月预算 ======
        // ====== 近 6 个月支出趋势 ======
        item(key = "trend") {
            PixelCard(modifier = Modifier.fillMaxWidth()) {
                Text(">> 近 6 个月支出", color = PixelGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                if (trend.isEmpty() || trend.all { it.second <= 0.0 }) {
                    Text("这几个月还没有支出记录", color = PixelTextDark, fontSize = 12.sp)
                } else {
                    MonthlyTrendChart(
                        trend = trend,
                        currentKey = year * 100 + month,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            }
        }

        item(key = "budget_stats") {
            PixelCard(modifier = Modifier.fillMaxWidth()) {
                Text(">> 本月预算", color = PixelGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                // 预算与支出统一折算成分再算，避免浮点误差影响 80% / 超支判定
                val limitCents = budget?.let { Money.toCents(it.amount) } ?: 0L
                if (limitCents <= 0L) {
                    Text("未设置预算", color = PixelTextDark, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "在「我的 → 月度预算」里设置上限后，这里会显示本月已用、剩余与占比。",
                        color = PixelTextDark,
                        fontSize = 12.sp
                    )
                } else {
                    val spentCents = Money.toCents(expense)
                    val remainingCents = limitCents - spentCents
                    val level = BudgetPolicy.level(limitCents, spentCents)
                    val percent = BudgetPolicy.usedPercent(limitCents, spentCents)
                    val barColor = when (level) {
                        BudgetLevel.EXCEEDED -> PixelRed
                        BudgetLevel.WARNING -> PixelOrange
                        null -> PixelGreen
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("已用 ${Money.format(spentCents)}", color = PixelRed, fontSize = 13.sp)
                        Text("/ ${Money.format(limitCents)}", color = PixelGold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .border(1.dp, PixelSurface, RoundedCornerShape(2.dp))
                            .background(PixelDarkBg)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (spentCents.toFloat() / limitCents).coerceIn(0f, 1f))
                                .background(barColor)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (remainingCents >= 0) "剩余 ${Money.format(remainingCents)}"
                            else "已超支 ${Money.format(-remainingCents)}",
                            color = if (remainingCents >= 0) PixelGreen else PixelRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "已用 $percent%",
                            color = if (level == null) PixelTextGray else barColor,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // ====== 每日支出像素柱状图 ======
        item {
            PixelCard(modifier = Modifier.fillMaxWidth()) {
                Text(">> 每日支出", color = PixelGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                if (dailyData.isEmpty()) {
                    Text("暂无数据", color = PixelTextDark, fontSize = 12.sp)
                } else {
                    PixelBarChart(
                        data = dailyData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                }
            }
        }

        // ====== 分类排行 ======
        item {
            Text(
                ">> 支出分类",
                color = PixelGold,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        if (categories.isEmpty()) {
            item {
                Text("暂无支出数据", color = PixelTextDark, fontSize = 12.sp)
            }
        } else {
            // 占比先看形状再看明细：环形图给比例感，下面的列表给具体金额
            item(key = "category_donut") {
                CategoryDonutCard(categories = categories)
            }
            val totalExpense = categories.sumOf { it.total }
            itemsIndexed(categories) { index, cat ->
                CategoryRankItem(
                    rank = index + 1,
                    category = cat,
                    totalExpense = totalExpense
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

/**
 * 像素风柱状图
 */
@Composable
private fun PixelBarChart(
    data: List<DailySummary>,
    modifier: Modifier = Modifier
) {
    val maxVal = data.maxOfOrNull { it.total } ?: 1.0
    val daysInMonth = DateUtil.daysInMonth(
        data.firstOrNull()?.let { DateUtil.currentYear() } ?: 2024,
        DateUtil.currentMonth()
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        for (d in 1..daysInMonth) {
            val point = data.find { it.day == d }
            val value = point?.total ?: 0.0
            val heightFraction = if (maxVal > 0) (value / maxVal).toFloat() else 0f

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                if (value > 0) {
                    Text(
                        text = value.toInt().toString(),
                        color = PixelTextDark,
                        fontSize = 7.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .fillMaxHeight(fraction = if (heightFraction > 0) heightFraction.coerceAtLeast(0.02f) else 0f)
                        .background(
                            if (value > 0) PixelRed else PixelSurface.copy(alpha = 0.2f),
                            RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                        )
                )
                if (d % 5 == 0 || d == 1) {
                    Text(
                        text = d.toString(),
                        color = PixelTextDark,
                        fontSize = 8.sp
                    )
                }
            }
        }
    }
}

/**
 * 分类排行项
 */
@Composable
private fun CategoryRankItem(
    rank: Int,
    category: CategorySummary,
    totalExpense: Double
) {
    val percentage = if (totalExpense > 0) (category.total / totalExpense * 100) else 0.0
    val barColor = ChartColors.getOrElse(rank - 1) { PixelTextDark }

    PixelCard(
        modifier = Modifier.fillMaxWidth(),
        borderWidth = 1.dp,
        borderColor = PixelSurface.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 排名
                Text(
                    text = "#$rank",
                    color = barColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.width(30.dp)
                )
                val catIcon = CategoryIcons.getByName(category.category)
                Icon(
                    imageVector = catIcon.icon,
                    contentDescription = category.category,
                    tint = catIcon.color,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = category.category,
                        color = PixelTextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${String.format("%.1f", percentage)}%",
                        color = PixelTextDark,
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                text = DateUtil.formatAmount(category.total),
                color = PixelRed,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 进度条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PixelDarkBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = (percentage / 100).toFloat().coerceIn(0f, 1f))
                    .background(barColor)
            )
        }
    }
}

/** 千位以上折成 k，避免 6 个柱子的金额标签互相挤在一起。 */
private fun compactAmount(value: Double): String {
    return when {
        value <= 0.0 -> "-"
        value >= 10000 -> String.format("%.0fw", value / 10000)
        value >= 1000 -> String.format("%.1fk", value / 1000)
        // 十元以下保留两位，否则 0.11 会显示成「0」，柱子和标签就对不上了
        value < 10 -> String.format("%.2f", value)
        else -> String.format("%.0f", value)
    }
}

/**
 * 近 N 个月支出柱状图：月份标签由 [TrendWindow] 保证跨年正确，
 * 当前选中的月份用金色高亮，其余用表面色，方便一眼看出「现在在哪」。
 */
@Composable
private fun MonthlyTrendChart(
    trend: List<Pair<YearMonth, Double>>,
    currentKey: Int,
    modifier: Modifier = Modifier
) {
    val maxVal = trend.maxOfOrNull { it.second } ?: 0.0
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        trend.forEach { (slot, total) ->
            val fraction = if (maxVal > 0) (total / maxVal).toFloat() else 0f
            val isCurrent = slot.key == currentKey
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    compactAmount(total),
                    color = if (isCurrent) PixelGold else PixelTextDark,
                    fontSize = 9.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(86.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (total > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .fillMaxHeight(fraction.coerceIn(0.04f, 1f))
                                .background(if (isCurrent) PixelGold else PixelSurface)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    slot.label,
                    color = if (isCurrent) PixelGold else PixelTextGray,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 支出分类占比环形图 + 前三名图例。
 * 用 Canvas 直接画，不引第三方图表库：一个圆环不值得多一个依赖。
 */
@Composable
private fun CategoryDonutCard(categories: List<CategorySummary>) {
    val total = categories.sumOf { it.total }
    PixelCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(104.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = size.minDimension * 0.2f
                    val diameter = size.minDimension - stroke
                    val topLeft = Offset(
                        (size.width - diameter) / 2f,
                        (size.height - diameter) / 2f
                    )
                    val arcSize = Size(diameter, diameter)
                    if (total <= 0) {
                        drawArc(
                            color = PixelSurface,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(stroke)
                        )
                    } else {
                        var start = -90f
                        categories.forEachIndexed { index, item ->
                            val sweep = (item.total / total * 360.0).toFloat()
                            drawArc(
                                color = ChartColors.getOrElse(index) { PixelTextDark },
                                startAngle = start,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(stroke, cap = StrokeCap.Butt)
                            )
                            start += sweep
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("支出", color = PixelTextGray, fontSize = 10.sp)
                    Text(
                        DateUtil.formatAmount(total),
                        color = PixelTextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.take(3).forEachIndexed { index, item ->
                    val percent = if (total > 0) item.total / total * 100 else 0.0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(ChartColors.getOrElse(index) { PixelTextDark })
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            item.category,
                            color = PixelTextWhite,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Text(
                            String.format("%.0f%%", percent),
                            color = PixelTextGray,
                            fontSize = 12.sp
                        )
                    }
                }
                if (categories.size > 3) {
                    Text(
                        "其余 ${categories.size - 3} 类共 ${DateUtil.formatAmount(total - categories.take(3).sumOf { it.total })}",
                        color = PixelTextDark,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
