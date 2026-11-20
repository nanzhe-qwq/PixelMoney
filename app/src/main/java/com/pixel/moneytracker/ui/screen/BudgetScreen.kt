package com.pixel.moneytracker.ui.screen

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.moneytracker.ui.component.PixelButton
import com.pixel.moneytracker.ui.component.PixelCard
import com.pixel.moneytracker.ui.theme.*
import com.pixel.moneytracker.ui.viewmodel.MainViewModel
import com.pixel.moneytracker.util.BudgetLevel
import com.pixel.moneytracker.util.BudgetPolicy
import com.pixel.moneytracker.util.DateUtil
import com.pixel.moneytracker.util.Money

@Composable
fun BudgetScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val year by viewModel.selectedYear.collectAsStateWithLifecycle()
    val month by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val budget by viewModel.budget.collectAsStateWithLifecycle()
    val expense by viewModel.monthExpense.collectAsStateWithLifecycle()

    // 预算与支出都折算成「分」再比较，避免浮点在 80% / 100% 边界上抖动。
    val limitCents = budget?.let { Money.toCents(it.amount) } ?: 0L
    val spentCents = Money.toCents(expense)
    val remainingCents = limitCents - spentCents
    val level = BudgetPolicy.level(limitCents, spentCents)
    val usedPercent = BudgetPolicy.usedPercent(limitCents, spentCents)

    var budgetText by remember(budget) { mutableStateOf(if (budget != null) Money.format(limitCents) else "") }
    var errorText by remember(budget) { mutableStateOf<String?>(null) }
    var saved by remember(budget) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelDarkBg)
    ) {
        // ====== 顶部栏 ======
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
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
                label = "back_scale"
            )
            IconButton(
                onClick = onBack,
                interactionSource = backInteraction,
                modifier = Modifier.scale(backScale)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = PixelGold, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text("月度预算", color = PixelGold, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }

        // ====== 内容 ======
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 当前月份 + 预算状态
            PixelCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = PixelGold.copy(alpha = 0.3f)
            ) {
                Text("${year}年${month}月", color = PixelTextGray, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))

                if (limitCents > 0L) {
                    AmountRow("已支出", Money.format(spentCents), PixelRed)
                    Spacer(modifier = Modifier.height(4.dp))
                    AmountRow("预算", Money.format(limitCents), PixelGold)
                    Spacer(modifier = Modifier.height(4.dp))
                    AmountRow(
                        if (remainingCents >= 0L) "剩余" else "已超支",
                        Money.format(if (remainingCents >= 0L) remainingCents else -remainingCents),
                        if (remainingCents >= 0L) PixelGreen else PixelRed
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    // 像素风进度条：颜色随档位变化（与首页一致）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .background(PixelShadow)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((spentCents.toFloat() / limitCents).coerceIn(0f, 1f))
                                .background(
                                    when (level) {
                                        BudgetLevel.EXCEEDED -> PixelRed
                                        BudgetLevel.WARNING -> PixelOrange
                                        null -> PixelGreen
                                    }
                                )
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        when (level) {
                            BudgetLevel.EXCEEDED -> "已用 $usedPercent%，已超出预算"
                            BudgetLevel.WARNING -> "已用 $usedPercent%，已触发 80% 提醒"
                            null -> "已用 $usedPercent%"
                        },
                        color = when (level) {
                            BudgetLevel.EXCEEDED -> PixelRed
                            BudgetLevel.WARNING -> PixelOrange
                            null -> PixelTextGray
                        },
                        fontSize = 12.sp
                    )
                } else {
                    Text("还未设置本月预算", color = PixelTextDark, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "设置后，本月支出达到 80% 与超支时各提醒一次。",
                        color = PixelTextDark,
                        fontSize = 12.sp
                    )
                }
            }

            // 设置预算
            PixelCard(modifier = Modifier.fillMaxWidth()) {
                Text("设置预算金额", color = PixelGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = budgetText,
                    onValueChange = {
                        budgetText = it.filter { c -> c.isDigit() || c == '.' }
                        errorText = null
                        saved = false
                    },
                    label = { Text("预算金额", color = PixelTextGray) },
                    prefix = { Text("¥ ", color = PixelGold) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = errorText != null,
                    supportingText = {
                        Text(
                            errorText ?: "最多两位小数，例如 2000 或 2000.50",
                            color = if (errorText != null) PixelRed else PixelTextDark,
                            fontSize = 12.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PixelGold,
                        unfocusedBorderColor = PixelSurface,
                        focusedTextColor = PixelTextWhite,
                        unfocusedTextColor = PixelTextWhite,
                        cursorColor = PixelGold,
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                PixelButton(
                    text = "保存预算",
                    onClick = {
                        val cents = Money.parseCents(budgetText)
                        if (cents == null) {
                            errorText = "请输入 0.01 ~ ${Money.format(Money.MAX_CENTS)} 之间的金额"
                        } else {
                            viewModel.setBudget(cents)
                            errorText = null
                            saved = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (saved) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("已保存 ${year}年${month}月 的预算", color = PixelGreen, fontSize = 12.sp)
                }

                if (limitCents > 0L) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.clearBudget() }) {
                        Text("清除本月预算", color = PixelTextGray, fontSize = 13.sp)
                    }
                }
            }

            // 提醒规则说明（同一周期同一阈值只提醒一次）
            PixelCard(modifier = Modifier.fillMaxWidth(), borderColor = PixelSurface) {
                Text(">> 提醒规则", color = PixelBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "· 支出达到预算的 80% 提醒一次\n" +
                        "· 支出达到 100%（超支）再提醒一次\n" +
                        "· 同一月份内不会重复提醒，下个月重新开始\n" +
                        "· 提醒统计的是「当前账本」的本月支出",
                    color = PixelTextGray,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun AmountRow(label: String, amount: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = PixelTextGray, fontSize = 13.sp)
        Text("¥$amount", color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
