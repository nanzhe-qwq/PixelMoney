package com.pixel.moneytracker.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pixel.moneytracker.MainActivity
import com.pixel.moneytracker.R
import java.time.LocalTime
import java.time.ZonedDateTime

internal fun nextReminder(now: ZonedDateTime, minutes: Int): ZonedDateTime {
    val time = LocalTime.ofSecondOfDay(minutes * 60L)
    val today = now.toLocalDate()
    val candidate = today.atTime(time).atZone(now.zone)
    return if (candidate.isAfter(now)) candidate else today.plusDays(1).atTime(time).atZone(now.zone)
}

internal object DailyReminder {
    const val OPEN_RECORD = "com.pixel.moneytracker.action.REMINDER_ADD_RECORD"
    const val CHANNEL = "daily_record_reminder"
    const val FIRE = "com.pixel.moneytracker.action.DAILY_REMINDER"
    const val NOTICE_ID = 4201
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
    private fun prefs(c: Context) = c.getSharedPreferences("daily_reminder", Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun time(c: Context) = prefs(c).getInt("minute", 21 * 60)

    fun configure(c: Context, enabled: Boolean, minute: Int) {
        require(minute in 0..1439)
        prefs(c).edit().putBoolean("enabled", enabled).putInt("minute", minute).apply()
        if (enabled) reschedule(c) else {
            existingAlarm(c)?.let {
                c.getSystemService(AlarmManager::class.java).cancel(it)
                it.cancel()
            }
            c.getSystemService(NotificationManager::class.java).cancel(NOTICE_ID)
        }
    }

    private fun alarmIntent(c: Context) = Intent(c, DailyReminderReceiver::class.java).setAction(FIRE)
    internal fun existingAlarm(c: Context): PendingIntent? =
        PendingIntent.getBroadcast(c, NOTICE_ID, alarmIntent(c), FLAGS or PendingIntent.FLAG_NO_CREATE)

    fun scheduleIfEnabled(c: Context) {
        if (enabled(c) && existingAlarm(c) == null) reschedule(c)
    }

    fun reschedule(c: Context) {
        if (!enabled(c)) return
        val minute = time(c)
        val next = nextReminder(ZonedDateTime.now(), minute)
        val intent = alarmIntent(c).putExtra("minute", minute).putExtra("day", next.toLocalDate().toEpochDay())
        val alarm = PendingIntent.getBroadcast(c, NOTICE_ID, intent, FLAGS or PendingIntent.FLAG_UPDATE_CURRENT)
        c.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), alarm
        )
    }

    fun createChannel(c: Context) {
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "每日记账提醒", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun canNotify(c: Context): Boolean {
        val manager = c.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun deliver(c: Context, alarm: Intent) {
        if (!enabled(c)) return
        reschedule(c)
        val now = ZonedDateTime.now()
        val today = now.toLocalDate().toEpochDay()
        // An old alarm can already be in flight after a time change, or arrive the following morning.
        if (alarm.getIntExtra("minute", -1) != time(c) || alarm.getLongExtra("day", -1L) != today ||
            prefs(c).getLong("lastDay", -1L) == today) return
        createChannel(c)
        if (!canNotify(c) || (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(
            Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) return
        val open = Intent(c, MainActivity::class.java).setAction(OPEN_RECORD)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(c, NOTICE_ID, open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("记账时间到了")
            .setContentText("点这里，记下今天的收支。")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        try {
            c.getSystemService(NotificationManager::class.java).notify(NOTICE_ID, notification)
            prefs(c).edit().putLong("lastDay", today).apply()
        } catch (_: SecurityException) {
            // Notification permission can be revoked between the check and notify().
        }
    }
}

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DailyReminder.FIRE -> DailyReminder.deliver(context, intent)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> DailyReminder.reschedule(context)
        }
    }
}
