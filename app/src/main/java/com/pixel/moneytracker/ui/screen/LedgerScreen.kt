package com.pixel.moneytracker.ui.screen

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.moneytracker.data.entity.Ledger
import com.pixel.moneytracker.ui.component.PixelCard
import com.pixel.moneytracker.ui.component.PixelTag
import com.pixel.moneytracker.ui.component.LedgerIcons
import com.pixel.moneytracker.ui.theme.*
import com.pixel.moneytracker.ui.viewmodel.MainViewModel

@Composable
fun LedgerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val ledgers by viewModel.allLedgers.collectAsStateWithLifecycle()
    val currentLedgerId by viewModel.currentLedgerId.collectAsStateWithLifecycle()

    // 弹窗状态
    var showAddDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedLedger by remember { mutableStateOf<Ledger?>(null) }

    // emoji 列表
    val emojiOptions = listOf("📝", "✈️", "📚", "💕", "🎯", "🏠", "💼", "🎮", "🍜", "💊", "🎁", "💰")

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
            Text("账本", color = PixelGold, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.weight(1f))
            // 添加账本按钮
            val addInteraction = remember { MutableInteractionSource() }
            val addPressed by addInteraction.collectIsPressedAsState()
            val addScale by animateFloatAsState(
                targetValue = if (addPressed) 0.8f else 1f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
                label = "add_scale"
            )
            IconButton(
                onClick = { showAddDialog = true },
                interactionSource = addInteraction,
                modifier = Modifier.scale(addScale)
            ) {
                Icon(Icons.Default.Add, "添加账本", tint = PixelGold, modifier = Modifier.size(24.dp))
            }
        }

        // ====== 账本列表 ======
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            items(ledgers, key = { it.id }) { ledger ->
                LedgerCard(
                    ledger = ledger,
                    isSelected = ledger.id == currentLedgerId,
                    onSelect = { viewModel.switchLedger(ledger) },
                    onRename = {
                        selectedLedger = ledger
                        showRenameDialog = true
                    },
                    onDelete = {
                        selectedLedger = ledger
                        showDeleteDialog = true
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // ====== 添加账本弹窗 ======
    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        var selectedEmoji by remember { mutableStateOf(emojiOptions[0]) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = PixelCardBg,
            titleContentColor = PixelTextWhite,
            title = { Text("新建账本", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 图标选择
                    Text("选择图标", color = PixelTextGray, fontSize = 12.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        emojiOptions.take(6).forEach { emoji ->
                            val iconData = LedgerIcons.getByEmoji(emoji)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        2.dp,
                                        if (emoji == selectedEmoji) PixelGold else PixelSurface,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .background(if (emoji == selectedEmoji) PixelGold.copy(alpha = 0.15f) else PixelSurface)
                                    .clickable { selectedEmoji = emoji },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconData.icon,
                                    contentDescription = null,
                                    tint = iconData.color,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        emojiOptions.drop(6).forEach { emoji ->
                            val iconData = LedgerIcons.getByEmoji(emoji)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        2.dp,
                                        if (emoji == selectedEmoji) PixelGold else PixelSurface,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .background(if (emoji == selectedEmoji) PixelGold.copy(alpha = 0.15f) else PixelSurface)
                                    .clickable { selectedEmoji = emoji },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconData.icon,
                                    contentDescription = null,
                                    tint = iconData.color,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // 名称输入
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("账本名称", color = PixelTextGray) },
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
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.addLedger(newName.trim(), selectedEmoji)
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("创建", color = PixelGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("取消", color = PixelTextGray)
                }
            }
        )
    }

    // ====== 改名弹窗 ======
    if (showRenameDialog && selectedLedger != null) {
        var editName by remember(selectedLedger) { mutableStateOf(selectedLedger!!.name) }

        AlertDialog(
            onDismissRequest = { showRenameDialog = false; selectedLedger = null },
            containerColor = PixelCardBg,
            titleContentColor = PixelTextWhite,
            title = { Text("重命名账本", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("新名称", color = PixelTextGray) },
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
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editName.isNotBlank() && selectedLedger != null) {
                            viewModel.renameLedger(selectedLedger!!.id, editName.trim())
                            showRenameDialog = false
                            selectedLedger = null
                        }
                    }
                ) {
                    Text("保存", color = PixelGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false; selectedLedger = null }) {
                    Text("取消", color = PixelTextGray)
                }
            }
        )
    }

    // ====== 删除确认弹窗 ======
    if (showDeleteDialog && selectedLedger != null) {
        val ledger = selectedLedger!!
        if (ledger.isDefault) {
            // 默认账本不能删除
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false; selectedLedger = null },
                containerColor = PixelCardBg,
                titleContentColor = PixelTextWhite,
                textContentColor = PixelTextGray,
                title = { Text("无法删除", fontWeight = FontWeight.Bold) },
                text = { Text("默认账本不能删除哦～") },
                confirmButton = {
                    TextButton(onClick = { showDeleteDialog = false; selectedLedger = null }) {
                        Text("知道了", color = PixelGold)
                    }
                }
            )
        } else {
            var recordCount by remember { mutableStateOf(0) }
            LaunchedEffect(ledger) {
                recordCount = viewModel.getLedgerRecordCount(ledger.id)
            }

            AlertDialog(
                onDismissRequest = { showDeleteDialog = false; selectedLedger = null },
                containerColor = PixelCardBg,
                titleContentColor = PixelTextWhite,
                textContentColor = PixelTextGray,
                title = { Text("删除账本", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        if (recordCount > 0)
                            "「${ledger.emoji} ${ledger.name}」下有 ${recordCount} 条记录\n删除后记录也会一并删除，确定要删除吗？"
                        else
                            "确定删除「${ledger.emoji} ${ledger.name}」吗？"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteLedger(ledger)
                        showDeleteDialog = false
                        selectedLedger = null
                    }) {
                        Text("删除", color = PixelRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false; selectedLedger = null }) {
                        Text("取消", color = PixelTextGray)
                    }
                }
            )
        }
    }
}

@Composable
private fun LedgerCard(
    ledger: Ledger,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label = "ledger_scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) PixelGold else PixelSurface.copy(alpha = 0.3f),
        animationSpec = tween(200),
        label = "ledger_border"
    )

    PixelCard(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true)
            ) { onSelect() },
        borderWidth = if (isSelected) 2.dp else 1.dp,
        borderColor = borderColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧：图标 + 名称 + 标签
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ledgerIcon = LedgerIcons.getByEmoji(ledger.emoji)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PixelSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ledgerIcon.icon,
                        contentDescription = ledger.name,
                        tint = ledgerIcon.color,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            ledger.name,
                            color = if (isSelected) PixelGold else PixelTextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (ledger.isDefault) {
                            Spacer(modifier = Modifier.width(8.dp))
                            PixelTag(text = "默认", color = PixelGold)
                        }
                        if (isSelected && !ledger.isDefault) {
                            Spacer(modifier = Modifier.width(8.dp))
                            PixelTag(text = "当前", color = PixelGreen)
                        }
                    }
                    Text(
                        if (isSelected) "当前使用中" else "点击切换到此账本",
                        color = if (isSelected) PixelGreen else PixelTextDark,
                        fontSize = 12.sp
                    )
                }
            }

            // 右侧：操作按钮
            Row {
                IconButton(
                    onClick = onRename,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Edit, "重命名", tint = PixelTextGray, modifier = Modifier.size(18.dp))
                }
                if (!ledger.isDefault) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, "删除", tint = PixelTextDark, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
