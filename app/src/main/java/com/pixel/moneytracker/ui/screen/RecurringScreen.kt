package com.pixel.moneytracker.ui.screen

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.moneytracker.data.entity.Frequency
import com.pixel.moneytracker.data.entity.RecordType
import com.pixel.moneytracker.data.entity.RecurringRecord
import com.pixel.moneytracker.ui.component.*
import com.pixel.moneytracker.ui.theme.*
import com.pixel.moneytracker.ui.viewmodel.MainViewModel
import com.pixel.moneytracker.util.Categories
import com.pixel.moneytracker.util.DateUtil

@Composable
fun RecurringScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val records by viewModel.recurringRecords.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<RecurringRecord?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(PixelDarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ====== 顶栏 ======
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PixelCardBg)
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = PixelGold)
                }
                Text(
                    "定时记账",
                    color = PixelGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "共 ${records.size} 条规则",
                    color = PixelTextDark,
                    fontSize = 12.sp
                )
            }

            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📋", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "还没有自动记账规则",
                            color = PixelTextGray,
                            fontSize = 14.sp
                        )
                        Text(
                            "点击右下角 + 添加固定支出/收入",
                            color = PixelTextDark,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(records, key = { it.id }) { record ->
                        RecurringItem(
                            record = record,
                            onToggle = { viewModel.toggleRecurringEnabled(record.id, it) },
                            onDelete = {
                                recordToDelete = record
                                showDeleteDialog = true
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }

        // FAB
        val fabInteraction = remember { MutableInteractionSource() }
        val fabPressed by fabInteraction.collectIsPressedAsState()
        val fabScale by animateFloatAsState(
            targetValue = if (fabPressed) 0.85f else 1f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
            label = "fab_scale"
        )
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = PixelGold,
            contentColor = PixelDarkBg,
            interactionSource = fabInteraction,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .scale(fabScale)
                .border(3.dp, PixelGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
        ) {
            Icon(Icons.Default.Add, "添加规则", modifier = Modifier.size(28.dp))
        }
    }

    // ====== 删除确认弹窗 ======
    if (showDeleteDialog && recordToDelete != null) {
        val r = recordToDelete!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; recordToDelete = null },
            containerColor = PixelCardBg,
            titleContentColor = PixelTextWhite,
            textContentColor = PixelTextGray,
            title = { Text("确认删除", fontWeight = FontWeight.Bold) },
            text = { Text("要删除定时记账规则「${r.note}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecurringRecord(r)
                    showDeleteDialog = false
                    recordToDelete = null
                }) {
                    Text("删除", color = PixelRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; recordToDelete = null }) {
                    Text("取消", color = PixelTextGray)
                }
            }
        )
    }

    // ====== 添加对话框 ======
    if (showAddDialog) {
        AddRecurringDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { amount, type, category, emoji, note, freq ->
                viewModel.addRecurringRecord(amount, type, category, emoji, note, freq)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun RecurringItem(
    record: RecurringRecord,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val amountColor = if (record.type == RecordType.EXPENSE) PixelRed else PixelGreen
    val sign = if (record.type == RecordType.EXPENSE) "-" else "+"

    PixelCard(
        modifier = Modifier.fillMaxWidth(),
        borderWidth = 1.dp,
        borderColor = if (record.enabled) PixelSurface else PixelTextDark.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧信息
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = record.emoji, fontSize = 32.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = record.category,
                            color = if (record.enabled) PixelTextWhite else PixelTextDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        PixelTag(
                            text = record.frequency.label,
                            color = PixelBlue
                        )
                    }
                    if (record.note.isNotBlank()) {
                        Text(
                            text = record.note,
                            color = PixelTextDark,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = "$sign${DateUtil.formatAmount(record.amount)}",
                        color = if (record.enabled) amountColor else PixelTextDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            // 右侧操作
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(
                    checked = record.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PixelGold,
                        checkedTrackColor = PixelGold.copy(alpha = 0.3f),
                        uncheckedThumbColor = PixelTextDark,
                        uncheckedTrackColor = PixelSurface
                    )
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        "删除",
                        tint = PixelTextDark,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * 添加自动记账规则对话框
 */
@Composable
private fun AddRecurringDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, RecordType, String, String, String, Frequency) -> Unit
) {
    var isExpense by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf(Categories.expenseCategories.first()) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedFreq by remember { mutableStateOf(Frequency.MONTHLY) }

    val type = if (isExpense) RecordType.EXPENSE else RecordType.INCOME
    val categories = if (isExpense) Categories.expenseCategories else Categories.incomeCategories

    LaunchedEffect(isExpense) {
        selectedCategory = if (isExpense) Categories.expenseCategories.first()
                           else Categories.incomeCategories.first()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PixelCardBg,
        title = {
            Text("添加自动记账", color = PixelGold, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 收支切换
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple("支出", true, PixelRed),
                        Triple("收入", false, PixelGreen)
                    ).forEach { (label, value, color) ->
                        val selected = isExpense == value
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(2.dp, if (selected) color else PixelSurface, RoundedCornerShape(4.dp))
                                .background(if (selected) color.copy(alpha = 0.2f) else PixelSurface.copy(alpha = 0.3f))
                                .clickable { isExpense = value }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (selected) color else PixelTextGray, fontSize = 13.sp)
                        }
                    }
                }

                // 金额
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("金额", color = PixelTextGray) },
                    prefix = { Text("¥ ", color = PixelGold) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PixelGold,
                        unfocusedBorderColor = PixelSurface,
                        focusedTextColor = PixelTextWhite,
                        unfocusedTextColor = PixelTextWhite,
                        cursorColor = PixelGold,
                    ),
                    singleLine = true
                )

                // 分类选择
                Text("分类", color = PixelTextGray, fontSize = 12.sp)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = 120.dp)
                ) {
                    items(categories, key = { it.name }) { cat ->
                        CategoryChip(
                            name = cat.name,
                            selected = selectedCategory.name == cat.name,
                            onClick = { selectedCategory = cat }
                        )
                    }
                }

                // 频率选择
                Text("频率", color = PixelTextGray, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Frequency.entries.forEach { freq ->
                        val selected = selectedFreq == freq
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(
                                    1.dp,
                                    if (selected) PixelBlue else PixelSurface,
                                    RoundedCornerShape(4.dp)
                                )
                                .background(if (selected) PixelBlue.copy(alpha = 0.2f) else PixelSurface.copy(alpha = 0.3f))
                                .clickable { selectedFreq = freq }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                freq.label,
                                color = if (selected) PixelBlue else PixelTextGray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // 备注
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
                    singleLine = true
                )
            }
        },
        confirmButton = {
            PixelButton(
                text = "添加",
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        onConfirm(amount, type, selectedCategory.name, selectedCategory.emoji, note, selectedFreq)
                    }
                }
            )
        },
        dismissButton = {
            PixelButton(
                text = "取消",
                onClick = onDismiss,
                bgColor = PixelSurface,
                textColor = PixelTextGray
            )
        }
    )
}
