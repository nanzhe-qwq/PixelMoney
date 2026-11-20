package com.pixel.moneytracker.ui.screen

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pixel.moneytracker.reminder.DailyReminder
import com.pixel.moneytracker.ui.component.PixelCard
import com.pixel.moneytracker.ui.theme.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
internal fun DailyReminderCard() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var enabled by remember { mutableStateOf(DailyReminder.enabled(context)) }
    var minute by remember { mutableIntStateOf(DailyReminder.time(context)) }
    var wantsEnable by rememberSaveable { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(DailyReminder.canNotify(context)) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    fun refreshPermission() {
        allowed = DailyReminder.canNotify(context)
        if (allowed && wantsEnable) {
            DailyReminder.configure(context, true, minute)
            enabled = true
            wantsEnable = false
        }
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPermission()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermission()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (pickingTime) {
        DisposableEffect(context) {
            val dialog = TimePickerDialog(context, { _, hour, min ->
                minute = hour * 60 + min
                DailyReminder.configure(context, enabled, minute)
            }, minute / 60, minute % 60, true)
            dialog.setOnDismissListener { pickingTime = false }
            dialog.show()
            onDispose { dialog.setOnDismissListener(null); dialog.dismiss() }
        }
    }
    PixelCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().toggleable(value = enabled, role = Role.Switch) { turnOn ->
            wantsEnable = turnOn
            if (!turnOn) {
                DailyReminder.configure(context, false, minute)
                enabled = false
            } else {
                DailyReminder.createChannel(context)
                refreshPermission()
                if (!allowed && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }, verticalAlignment = Alignment.CenterVertically) {
            Text("每日记账提醒", Modifier.weight(1f), color = PixelGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Switch(checked = enabled, onCheckedChange = null)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("提醒时间", Modifier.weight(1f), color = PixelTextGray, fontSize = 14.sp)
            TextButton(onClick = { pickingTime = true }) {
                Text(LocalTime.ofSecondOfDay(minute * 60L).format(DateTimeFormatter.ofPattern("HH:mm")), color = PixelGold)
            }
        }
        if (!allowed && (enabled || wantsEnable)) {
            Text("系统通知已关闭，开启后才能收到提醒。", color = PixelTextGray, fontSize = 14.sp)
            TextButton(onClick = {
                val manager = context.getSystemService(android.app.NotificationManager::class.java)
                val action = if (manager.areNotificationsEnabled()) Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                    else Settings.ACTION_APP_NOTIFICATION_SETTINGS
                context.startActivity(Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, DailyReminder.CHANNEL))
            }) { Text("打开通知设置", color = PixelGold) }
        } else if (enabled) {
            Text("按本地时间安排，系统可能延后提醒。", color = PixelTextGray, fontSize = 12.sp)
        }
    }
}
