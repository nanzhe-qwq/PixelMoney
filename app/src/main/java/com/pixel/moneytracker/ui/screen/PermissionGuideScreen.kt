package com.pixel.moneytracker.ui.screen

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pixel.moneytracker.service.PaymentNotificationService
import com.pixel.moneytracker.ui.component.PixelCard
import com.pixel.moneytracker.ui.theme.*

/**
 * 权限引导页（首次打开显示）
 */
@Composable
fun PermissionGuideScreen(
    onDone: () -> Unit
) {
    val context = LocalContext.current

    // 刷新权限状态
    var notificationEnabled by remember { mutableStateOf(isNotificationPermissionEnabled(context)) }
    var notificationListenerEnabled by remember { mutableStateOf(isNotificationEnabled(context)) }
    var overlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityEnabled(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationEnabled = isNotificationPermissionEnabled(context)
                notificationListenerEnabled = isNotificationEnabled(context)
                overlayEnabled = Settings.canDrawOverlays(context)
                accessibilityEnabled = isAccessibilityEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allGranted = notificationEnabled && notificationListenerEnabled && overlayEnabled

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelDarkBg)
            // 小屏（如 720x1280）上内容会超出屏幕，必须可滚动，否则「稍后设置」按钮够不到
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // 像素风标题框
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .border(2.dp, PixelGold.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .background(PixelGold.copy(alpha = 0.1f))
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 月薪喵图标
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(2.dp, PixelGold.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .background(PixelDarkBg),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = com.pixel.moneytracker.R.drawable.ic_salary_cat),
                        contentDescription = "月薪喵",
                        modifier = Modifier.size(72.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "像素记账",
                    color = PixelGold,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "开启权限，轻松记账",
                    color = PixelTextGray,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // 权限列表
        PermissionItem(
            icon = Icons.Default.Notifications,
            title = "通知栏权限",
            subtitle = "显示支付监听状态和记账提醒",
            granted = notificationEnabled,
            onClick = {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    })
                } else {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra("app_package", context.packageName)
                        putExtra("app_uid", context.applicationInfo.uid)
                    })
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            icon = Icons.Default.NotificationsActive,
            title = "通知读取权限",
            subtitle = "监听支付宝/微信等支付通知，自动弹窗记账",
            granted = notificationListenerEnabled,
            onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            icon = Icons.Default.Layers,
            title = "悬浮窗权限",
            subtitle = "支付成功后弹出确认窗口",
            granted = overlayEnabled,
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}"))
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            icon = Icons.Default.Accessibility,
            title = "账单读取权限（可选）",
            subtitle = "进入支付宝/微信账单详情时自动弹窗",
            granted = accessibilityEnabled,
            onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 提示
        if (!allGranted) {
            Text(
                "请开启以上权限以获得完整体验",
                color = PixelOrange,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 开始按钮
        val btnInteraction = remember { MutableInteractionSource() }
        val btnPressed by btnInteraction.collectIsPressedAsState()
        val btnScale by animateFloatAsState(
            targetValue = if (btnPressed) 0.95f else 1f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
            label = "btn_scale"
        )
        // 像素风开始按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .scale(btnScale)
                .clip(RoundedCornerShape(4.dp))
                .border(2.dp, if (allGranted) PixelGold else PixelGold.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .background(if (allGranted) PixelGold else PixelGold.copy(alpha = 0.3f))
                .clickable(
                    interactionSource = btnInteraction,
                    indication = null
                ) { onDone() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (allGranted) "开始记账 ✨" else "稍后设置，先用用看",
                color = if (allGranted) PixelDarkBg else PixelGold,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label = "perm_scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (granted) PixelGreen else PixelSurface,
        animationSpec = tween(200),
        label = "perm_border"
    )

    PixelCard(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { if (!granted) onClick() },
        borderWidth = 1.dp,
        borderColor = borderColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 像素风图标框
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(2.dp))  // 像素风用更小的圆角
                    .border(2.dp, if (granted) PixelGreen else PixelGold, RoundedCornerShape(2.dp))  // 更粗的边框
                    .background(if (granted) PixelGreen.copy(alpha = 0.15f) else PixelGold.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (granted) PixelGreen else PixelGold,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = PixelTextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, color = PixelTextDark, fontSize = 11.sp, lineHeight = 16.sp)
            }
            if (granted) {
                // 像素风勾选
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PixelGreen.copy(alpha = 0.2f))
                        .border(1.dp, PixelGreen.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = PixelGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                // 像素风按钮
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .border(1.dp, PixelGold, RoundedCornerShape(2.dp))
                        .background(PixelGold.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("去开启", color = PixelGold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }
}

private fun isNotificationEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (TextUtils.isEmpty(flat)) return false
    val names = flat.split(":")
    val componentName = ComponentName(context, PaymentNotificationService::class.java).flattenToString()
    return names.any { it == componentName }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val serviceName = ComponentName(context, com.pixel.moneytracker.service.PaymentAccessibilityService::class.java).flattenToString()
    try {
        val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (flat.isNullOrEmpty()) return false
        return flat.split(":").any { it.equals(serviceName, ignoreCase = true) }
    } catch (e: Exception) {
        return false
    }
}

private fun isNotificationPermissionEnabled(context: Context): Boolean {
    return if (android.os.Build.VERSION.SDK_INT >= 33) {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true // Android 13 以下不需要运行时权限
    }
}
