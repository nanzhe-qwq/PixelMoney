package com.pixel.moneytracker.ui.screen

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.pixel.moneytracker.service.PaymentAppSettings
import com.pixel.moneytracker.service.FloatingWindowService
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pixel.moneytracker.service.PaymentNotificationService
import com.pixel.moneytracker.ui.component.PixelCard
import com.pixel.moneytracker.ui.theme.*

@Composable
fun PaymentScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var autoEnabled by remember { mutableStateOf(PaymentAppSettings.enabled(context)) }

    var notificationEnabled by remember { mutableStateOf(isPaymentNotificationEnabled(context)) }
    var overlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    // 每次页面恢复时刷新权限状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationEnabled = isPaymentNotificationEnabled(context)
                overlayEnabled = Settings.canDrawOverlays(context)
                accessibilityEnabled = isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            Text("支付记账", color = PixelGold, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }

        // ====== 内容 ======
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自动记账", color = PixelTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(if (autoEnabled) "识别后弹窗，由你确认保存" else "已暂停识别", color = PixelTextGray, fontSize = 14.sp)
                }
                Switch(checked = autoEnabled, onCheckedChange = {
                    autoEnabled = it
                    PaymentAppSettings.setEnabled(context, "enabled", it)
                    if (!it) context.stopService(Intent(context, FloatingWindowService::class.java))
                })
            }
            // 说明卡片
            PixelCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = PixelGold.copy(alpha = 0.3f)
            ) {
                Text("🔔", fontSize = 32.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "支付自动记账",
                    color = PixelGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "支付或收款成功后弹窗确认；微信、支付宝支持账单详情读取。淘宝、拼多多、京东（含外卖）、美团、饿了么支持明确的支付回执与实付字段，实际可用性取决于页面和通知内容。",
                    color = PixelTextGray,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }

            // 通知监听权限
            PermissionCard(
                title = "通知读取权限",
                description = "读取支付、外卖、购物、打车等App通知",
                enabled = notificationEnabled,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            )

            // 悬浮窗权限
            PermissionCard(
                title = "悬浮窗权限",
                description = "支付成功后弹窗确认记账",
                enabled = overlayEnabled,
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"))
                    )
                }
            )

            // 无障碍权限（账单详情读取）
            PermissionCard(
                title = "账单读取权限",
                description = "读取已开启应用的支付回执及订单、账单详情",
                enabled = accessibilityEnabled,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )

            Text("识别应用", color = PixelGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("以下应用可单独关闭，其他原有通知来源随总开关控制。", color = PixelTextGray, fontSize = 14.sp)
            PaymentAppSettings.screenApps.forEach { (pkg, name) ->
                var selected by remember(pkg) { mutableStateOf(PaymentAppSettings.enabled(context, pkg)) }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (name == "京东") "京东 · 含京东外卖" else name, color = PixelTextWhite, modifier = Modifier.weight(1f))
                    Switch(checked = selected, enabled = autoEnabled, onCheckedChange = {
                        selected = it
                        PaymentAppSettings.setEnabled(context, pkg, it)
                    })
                }
            }
            OutlinedButton(enabled = autoEnabled && overlayEnabled, onClick = {
                try {
                    context.startService(Intent(context, FloatingWindowService::class.java).apply {
                        putExtra(FloatingWindowService.EXTRA_AMOUNT, 0.01)
                        putExtra(FloatingWindowService.EXTRA_SOURCE, "弹窗测试")
                        putExtra(FloatingWindowService.EXTRA_NOTE, "测试样本，点击忽略即可")
                    })
                } catch (_: Exception) {
                    Toast.makeText(context, "无法显示弹窗，请检查悬浮窗及后台弹出权限", Toast.LENGTH_LONG).show()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("测试记账弹窗") }
            Text("澎湃 OS：若后台没有弹窗，请在系统应用权限中允许后台弹出界面，并检查省电限制。未支付订单不会记账；同一交易的多渠道提示请核对后忽略重复项。", color = PixelTextGray, fontSize = 14.sp)
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }, modifier = Modifier.fillMaxWidth()) { Text("打开系统应用权限") }
            Text("澎湃 OS：权限管理 → 其他权限 → 后台弹出界面，设为始终允许。此项与显示悬浮窗是两个独立权限。", color = PixelTextGray, fontSize = 14.sp)
            // 状态提示
            if (autoEnabled && notificationEnabled && overlayEnabled && accessibilityEnabled) {
                PixelCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = PixelGreen.copy(alpha = 0.4f)
                ) {
                    Text(
                        "基础权限已开启",
                        color = PixelGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "通知监听与账单读取已开启。请另外确认系统的后台弹出界面权限，并用真实支付完成页检查弹窗效果。",
                        color = PixelTextGray,
                        fontSize = 13.sp
                    )
                }
            } else {
                PixelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (!autoEnabled) "自动记账已暂停" else "还需要开启以上权限",
                        color = PixelOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "点击上方未开启的权限项，跳转系统设置开启",
                        color = PixelTextGray,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (enabled) PixelGreen else PixelRed,
        animationSpec = tween(200),
        label = "perm_border"
    )
    val bgColor by animateColorAsState(
        targetValue = if (enabled) PixelGreen.copy(alpha = 0.1f) else PixelRed.copy(alpha = 0.1f),
        animationSpec = tween(200),
        label = "perm_bg"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label = "perm_scale"
    )

    PixelCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                .background(bgColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onClick() }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = PixelTextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, color = PixelTextGray, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (enabled) {
                Text("已开启 ✓", color = PixelGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("未开启", color = PixelRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.Warning, "警告", tint = PixelRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun isPaymentNotificationEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (TextUtils.isEmpty(flat)) return false
    val names = flat.split(":")
    val componentName = ComponentName(context, PaymentNotificationService::class.java).flattenToString()
    return names.any { it == componentName }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val serviceName = ComponentName(context, com.pixel.moneytracker.service.PaymentAccessibilityService::class.java).flattenToString()
    try {
        val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (flat.isNullOrEmpty()) return false
        return flat.split(":").any { it.equals(serviceName, ignoreCase = true) }
    } catch (e: Exception) {
        return false
    }
}
