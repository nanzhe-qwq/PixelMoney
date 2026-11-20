package com.pixel.moneytracker.ui.screen

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.moneytracker.data.entity.Record
import com.pixel.moneytracker.data.entity.RecordType
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.pixel.moneytracker.ui.component.*
import com.pixel.moneytracker.ui.theme.*
import com.pixel.moneytracker.ui.viewmodel.MainViewModel
import com.pixel.moneytracker.util.Categories
import com.pixel.moneytracker.util.DateUtil

@Composable
fun AddRecordScreen(
    viewModel: MainViewModel,
    editRecord: Record? = null,
    onDone: () -> Unit
) {
    val isEditMode = editRecord != null

    // 编辑模式下用原记录的值初始化，新增模式默认支出
    var isExpense by remember {
        mutableStateOf(editRecord?.type != RecordType.INCOME)
    }
    val type = if (isExpense) RecordType.EXPENSE else RecordType.INCOME
    val originalCategory = editRecord?.takeIf { it.type == type }?.let { Categories.Category(it.category, it.emoji, it.type) }
    val presets = Categories.getByType(type)
    val categories = if (originalCategory != null && presets.none { it.name == originalCategory.name })
        listOf(originalCategory) + presets else presets
    var selectedCategory by remember(type, editRecord?.id) { mutableStateOf(originalCategory ?: presets.first()) }
    var amountText by remember {
        mutableStateOf(editRecord?.let {
            if (it.amount == it.amount.toLong().toDouble())
                it.amount.toLong().toString()
            else
                it.amount.toString()
        } ?: "")
    }
    var note by remember { mutableStateOf(editRecord?.note ?: "") }
    var showExchange by remember { mutableStateOf(false) }
    var lastExchangeMemo by remember { mutableStateOf("") }
    if (showExchange) {
        CurrencyExchangeDialog(onDismiss = { showExchange = false }, onApply = { yuan, memo ->
            amountText = yuan
            val previous = if (lastExchangeMemo.isNotEmpty() && (note == lastExchangeMemo || note.endsWith("\n$lastExchangeMemo")))
                note.removeSuffix(lastExchangeMemo).removeSuffix("\n") else note
            note = if (previous.isEmpty()) memo else "$previous\n$memo"
            lastExchangeMemo = memo
            showExchange = false
        })
    }

    // 编辑模式保留原记录的日期
    val editYear = editRecord?.year
    val editMonth = editRecord?.month
    val editDay = editRecord?.day

    val year = editYear ?: viewModel.selectedYear.collectAsStateWithLifecycle().value
    val month = editMonth ?: viewModel.selectedMonth.collectAsStateWithLifecycle().value
    val day = editDay ?: (viewModel.selectedDay.collectAsStateWithLifecycle().value ?: DateUtil.currentDay())
        .coerceIn(1, DateUtil.daysInMonth(year, month))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelDarkBg)
            .padding(16.dp)
    ) {
        // ====== 顶部栏 ======
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val closeInteraction = remember { MutableInteractionSource() }
            val closePressed by closeInteraction.collectIsPressedAsState()
            val closeScale by animateFloatAsState(
                targetValue = if (closePressed) 0.8f else 1f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
                label = "close_scale"
            )
            IconButton(
                onClick = onDone,
                interactionSource = closeInteraction,
                modifier = Modifier.scale(closeScale)
            ) {
                Icon(Icons.Default.Close, "关闭", tint = PixelTextWhite)
            }
            Text(
                text = if (isEditMode) "编辑记录" else DateUtil.formatDate(year, month, day),
                color = if (isEditMode) PixelGold else PixelTextGray,
                fontSize = if (isEditMode) 16.sp else 13.sp,
                fontWeight = if (isEditMode) FontWeight.Bold else FontWeight.Normal
            )
            TextButton(onClick = { showExchange = true }) { Text("汇率换算", color = PixelGold) }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ====== 收入/支出切换（带动画） ======
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            // 支出按钮
            TypeToggleButton(
                text = "支出",
                borderColor = PixelRed,
                isSelected = isExpense,
                isLeft = true,
                onClick = { isExpense = true }
            )
            // 收入按钮
            TypeToggleButton(
                text = "收入",
                borderColor = PixelGreen,
                isSelected = !isExpense,
                isLeft = false,
                onClick = { isExpense = false }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ====== 金额显示 ======
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .border(2.dp, PixelGold, RoundedCornerShape(2.dp))
                .background(PixelCardBg)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text("¥", color = PixelGold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (amountText.isEmpty()) "0" else amountText,
                    color = PixelTextWhite,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ====== 分类选择 ======
        Text("选择分类", color = PixelTextGray, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.heightIn(max = 200.dp)
        ) {
            items(
                categories,
                key = { it.name },
                contentType = { "category_chip" }
            ) { cat ->
                CategoryChip(
                    name = cat.name,
                    selected = selectedCategory.name == cat.name,
                    onClick = { selectedCategory = cat }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ====== 备注输入 ======
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("备注（可选）", color = PixelTextGray) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PixelGold,
                unfocusedBorderColor = PixelSurface,
                focusedTextColor = PixelTextWhite,
                unfocusedTextColor = PixelTextWhite,
                cursorColor = PixelGold,
            ),
            shape = RoundedCornerShape(2.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ====== 数字键盘 ======
        PixelNumPad(
            onNumber = { key ->
                // 限制小数点后2位
                if (key == ".") {
                    if (!amountText.contains(".")) {
                        amountText = if (amountText.isEmpty()) "0." else "$amountText."
                    }
                } else {
                    val dotIndex = amountText.indexOf(".")
                    if (dotIndex >= 0 && amountText.length - dotIndex > 2) return@PixelNumPad
                    if (amountText.length < 10) {
                        amountText += key
                    }
                }
            },
            onDelete = {
                if (amountText.isNotEmpty()) {
                    amountText = amountText.dropLast(1)
                }
            },
            onConfirm = {
                val amount = amountText.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    if (isEditMode && editRecord != null) {
                        viewModel.updateRecord(
                            editRecord.copy(
                                amount = amount,
                                type = type,
                                category = selectedCategory.name,
                                emoji = selectedCategory.emoji,
                                note = note,
                                year = year,
                                month = month,
                                day = day
                            )
                        )
                    } else {
                        viewModel.addRecord(
                            amount = amount,
                            type = type,
                            category = selectedCategory.name,
                            emoji = selectedCategory.emoji,
                            note = note,
                            year = year,
                            month = month,
                            day = day
                        )
                    }
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 收支类型切换按钮（带选中动画）
 */
@Composable
private fun TypeToggleButton(
    text: String,
    borderColor: androidx.compose.ui.graphics.Color,
    isSelected: Boolean,
    isLeft: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label = "type_btn_scale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) borderColor else PixelSurface,
        animationSpec = tween(200),
        label = "type_btn_bg"
    )

    val shape = if (isLeft)
        RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp)
    else
        RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(shape)
            .border(2.dp, borderColor, shape)
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true)
            ) { onClick() }
            .padding(horizontal = 32.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = PixelTextWhite, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    }
}
