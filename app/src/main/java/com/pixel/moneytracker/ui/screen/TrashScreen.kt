package com.pixel.moneytracker.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixel.moneytracker.data.entity.Record
import com.pixel.moneytracker.data.entity.RecordType
import com.pixel.moneytracker.ui.component.CategoryIcons
import com.pixel.moneytracker.ui.component.PixelCard
import com.pixel.moneytracker.ui.theme.*
import com.pixel.moneytracker.ui.viewmodel.MainViewModel
import com.pixel.moneytracker.util.DateUtil
import com.pixel.moneytracker.util.TrashPolicy

/**
 * 回收站：删除的账单先来这里，[TrashPolicy.RETENTION_DAYS] 天内都能找回。
 * 过期由 App 启动时统一清理，界面只展示剩余天数，不做后台定时任务。
 */
@Composable
fun TrashScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit = {}
) {
    val records by viewModel.trashRecords.collectAsStateWithLifecycle()
    var purgeTarget by remember { mutableStateOf<Record?>(null) }
    var showEmptyDialog by remember { mutableStateOf(false) }

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
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = PixelGold,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text("回收站", color = PixelGold, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.weight(1f))
            if (records.isNotEmpty()) {
                TextButton(onClick = { showEmptyDialog = true }) {
                    Text("清空", color = PixelRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "删除的账单会在这里保留 ${TrashPolicy.RETENTION_DAYS} 天，到期自动清除。",
                color = PixelTextGray,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🗑", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "回收站是空的\n删除的账单会先来到这里",
                        color = PixelTextDark,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val now = System.currentTimeMillis()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    TrashRow(
                        record = record,
                        remainingDays = TrashPolicy.remainingDays(record.deletedAt ?: now, now),
                        onRestore = { viewModel.restoreRecord(record.id) },
                        onPurge = { purgeTarget = record }
                    )
                }
            }
        }
    }

    purgeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { purgeTarget = null },
            title = { Text("彻底删除？") },
            text = { Text("这笔账将被永久删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.purgeRecord(target.id)
                    purgeTarget = null
                }) { Text("彻底删除", color = PixelRed) }
            },
            dismissButton = {
                TextButton(onClick = { purgeTarget = null }) { Text("取消") }
            }
        )
    }

    if (showEmptyDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyDialog = false },
            title = { Text("清空回收站？") },
            text = { Text("${records.size} 笔账单将被永久删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.emptyTrash()
                    showEmptyDialog = false
                }) { Text("清空", color = PixelRed) }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun TrashRow(
    record: Record,
    remainingDays: Int,
    onRestore: () -> Unit,
    onPurge: () -> Unit
) {
    val icon = CategoryIcons.getByName(record.category)
    val sign = if (record.type == RecordType.EXPENSE) "-" else "+"
    PixelCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(icon.color.copy(alpha = 0.15f))
                    .border(1.dp, icon.color.copy(alpha = 0.4f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon.icon, record.category, tint = icon.color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(record.category, color = PixelTextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${record.month}月${record.day}日" + if (record.note.isNotBlank()) " · ${record.note}" else "",
                    color = PixelTextDark,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            Text(
                "$sign${DateUtil.formatAmount(record.amount)}",
                color = if (record.type == RecordType.EXPENSE) PixelRed else PixelGreen,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (remainingDays > 0) "还剩 $remainingDays 天" else "即将清除",
                color = PixelTextGray,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRestore, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Text("恢复", color = PixelGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onPurge, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.DeleteForever,
                    "彻底删除",
                    tint = PixelRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
