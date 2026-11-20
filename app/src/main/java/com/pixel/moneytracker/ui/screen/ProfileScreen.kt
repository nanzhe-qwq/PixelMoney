package com.pixel.moneytracker.ui.screen

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.moneytracker.ui.component.PixelCard
import com.pixel.moneytracker.ui.theme.*
import com.pixel.moneytracker.ui.viewmodel.MainViewModel

@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToRecurring: () -> Unit = {},
    onNavigateToPayment: () -> Unit = {},
    onNavigateToBudget: () -> Unit = {},
    onNavigateToTrash: () -> Unit = {}
) {
    val totalDays by viewModel.totalRecordingDays.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalRecordCount.collectAsStateWithLifecycle()
    val streak by viewModel.consecutiveDays.collectAsStateWithLifecycle()
    val trashCount by viewModel.trashCount.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelDarkBg)
    ) {
        // ====== 顶部栏：← 返回  我的  ⚙️ 设置 ======
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：返回按钮
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

            // 中：标题
            Text(
                "我的",
                color = PixelGold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f)
            )

            // 右：设置图标
            val settingsInteraction = remember { MutableInteractionSource() }
            val settingsPressed by settingsInteraction.collectIsPressedAsState()
            val settingsScale by animateFloatAsState(
                targetValue = if (settingsPressed) 0.8f else 1f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
                label = "settings_scale"
            )
            IconButton(
                onClick = onNavigateToSettings,
                interactionSource = settingsInteraction,
                modifier = Modifier.scale(settingsScale)
            ) {
                Icon(Icons.Default.Settings, "设置", tint = PixelTextGray, modifier = Modifier.size(22.dp))
            }
        }

        // ====== 内容区域 ======
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 用户头像 + 昵称
            PixelCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = PixelGold.copy(alpha = 0.4f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .border(3.dp, PixelGold, CircleShape)
                            .background(PixelSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, "头像", tint = PixelGold, modifier = Modifier.size(36.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("像素记账用户", color = PixelTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("坚持记账，理性消费 ✨", color = PixelTextGray, fontSize = 13.sp)
                    }
                }
            }

            // 统计概览
            PixelCard(modifier = Modifier.fillMaxWidth()) {
                Text("记账统计", color = PixelGold, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("记账天数", "$totalDays", PixelGreen)
                    StatItem("总笔数", "$totalCount", PixelBlue)
                    StatItem("连续天数", "$streak", PixelOrange)
                }
            }

            // ====== 功能菜单 ======
            ProfileMenuItem(
                icon = Icons.Default.Settings,
                title = "月度预算",
                subtitle = "本月上限、超支提醒与每日可用"
            ) { onNavigateToBudget() }

            ProfileMenuItem(
                icon = Icons.Default.Refresh,
                title = "定时记账",
                subtitle = "定时自动记录固定收支"
            ) { onNavigateToRecurring() }

            ProfileMenuItem(
                icon = Icons.Default.Info,
                title = "支付记账",
                subtitle = "监听支付宝/微信支付自动记账"
            ) { onNavigateToPayment() }

            ProfileMenuItem(
                icon = Icons.Default.Delete,
                title = "回收站",
                subtitle = if (trashCount > 0) "$trashCount 笔待清理，可恢复" else "删除的账单在这里保留 30 天"
            ) { onNavigateToTrash() }

            ProfileMenuItem(
                icon = Icons.Default.Person,
                title = "关于",
                subtitle = "像素记账 v${com.pixel.moneytracker.BuildConfig.VERSION_NAME}"
            ) { }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = PixelTextGray, fontSize = 12.sp)
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label = "menu_scale"
    )

    PixelCard(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true)
            ) { onClick() },
        borderWidth = 1.dp,
        borderColor = PixelSurface.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PixelSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = title, tint = PixelGold, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, color = PixelTextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = PixelTextDark, fontSize = 12.sp)
                }
            }
            Text("›", color = PixelTextDark, fontSize = 20.sp)
        }
    }
}
