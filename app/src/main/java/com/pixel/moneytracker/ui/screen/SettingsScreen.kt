package com.pixel.moneytracker.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.moneytracker.ui.component.PixelCard
import com.pixel.moneytracker.ui.viewmodel.MainViewModel
import com.pixel.moneytracker.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit = {}
) {
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
            Text("设置", color = PixelGold, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }

        // ====== 内容 ======
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BillsTransfer(viewModel)
            BackupAction(viewModel)
            DailyReminderCard()
            // ====== 关于 ======
            PixelCard(modifier = Modifier.fillMaxWidth()) {
                Text("关于", color = PixelGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("像素记账 v${com.pixel.moneytracker.BuildConfig.VERSION_NAME}", color = PixelTextWhite, fontSize = 14.sp)
                Text("Pixel Money Tracker", color = PixelTextGray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "用像素的方式记录每一笔\n(ﾉ◕ヮ◕)ﾉ*:・ﾟ✧",
                    color = PixelTextDark,
                    fontSize = 12.sp
                )
            }
        }
    }
}
