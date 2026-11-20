package com.pixel.moneytracker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixel.moneytracker.reminder.DailyReminder
import com.pixel.moneytracker.reminder.DailyReminderReceiver
import java.time.ZonedDateTime
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyReminderDeviceTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val isolatedPrefs = app.getSharedPreferences("reminder_instrumentation", Context.MODE_PRIVATE)
    private val context = object : ContextWrapper(app) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = isolatedPrefs
    }
    private var ownsAlarm = false

    @Before fun isolate() {
        assumeFalse("Existing user reminder must remain untouched", DailyReminder.enabled(app)
            || DailyReminder.existingAlarm(app) != null
            || app.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == DailyReminder.NOTICE_ID })
        isolatedPrefs.edit().clear().commit()
        ownsAlarm = true
    }

    @After fun cleanup() {
        if (ownsAlarm) {
            DailyReminder.configure(context, false, 0)
            isolatedPrefs.edit().clear().commit()
        }
    }

    @Test fun enablingReschedulingAndDisablingUseOneAlarm() {
        DailyReminder.configure(context, true, 1290)
        val first = DailyReminder.existingAlarm(context)
        assertNotNull(first)
        DailyReminderReceiver().onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        assertEquals(first, DailyReminder.existingAlarm(context))
        DailyReminder.configure(context, false, 1290)
        assertNull(DailyReminder.existingAlarm(context))
    }

    @Test fun dueReminderPostsNotificationWithDirectAddRecordAction() {
        DailyReminder.createChannel(context)
        assumeTrue("Notification permission must already be enabled", DailyReminder.canNotify(context))
        val now = ZonedDateTime.now()
        val minute = now.hour * 60 + now.minute
        DailyReminder.configure(context, true, minute)
        DailyReminderReceiver().onReceive(context, Intent(DailyReminder.FIRE)
            .putExtra("minute", minute).putExtra("day", now.toLocalDate().toEpochDay()))
        val manager = app.getSystemService(NotificationManager::class.java)
        val end = android.os.SystemClock.elapsedRealtime() + 3000
        var notification = manager.activeNotifications.firstOrNull { it.id == DailyReminder.NOTICE_ID }
        while (notification == null && android.os.SystemClock.elapsedRealtime() < end) {
            android.os.SystemClock.sleep(50)
            notification = manager.activeNotifications.firstOrNull { it.id == DailyReminder.NOTICE_ID }
        }
        val posted = requireNotNull(notification).notification
        val open = Intent(app, MainActivity::class.java).setAction(DailyReminder.OPEN_RECORD)
        val tap = PendingIntent.getActivity(app, DailyReminder.NOTICE_ID, open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
        assertNotNull(tap)
        assertEquals(tap, posted.contentIntent)
        assertEquals(DailyReminder.CHANNEL, posted.channelId)
        assertNotNull(DailyReminder.existingAlarm(context))
    }
}
