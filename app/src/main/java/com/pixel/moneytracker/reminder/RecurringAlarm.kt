package com.pixel.moneytracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pixel.moneytracker.data.database.AppDatabase
import com.pixel.moneytracker.util.RecurringSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 周期记账的闹钟调度（沿用 [DailyReminder] 的 AlarmManager + BroadcastReceiver 基建，
 * 不引入 WorkManager 等新依赖）。
 *
 * 用 `setAndAllowWhileIdle` 而不是精确闹钟：Doze 下允许唤醒，且不需要
 * SCHEDULE_EXACT_ALARM 权限；周期记账按「天」计时，分钟级误差可以接受。
 */
internal object RecurringAlarm {

    const val ACTION_CHECK = "com.pixel.moneytracker.action.RECURRING_CHECK"
    const val TAG = "RecurringAlarm"

    private const val REQUEST = 4301
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT

    private fun alarmIntent(context: Context) =
        Intent(context, RecurringAlarmReceiver::class.java).setAction(ACTION_CHECK)

    private fun pendingIntent(context: Context, extra: Int): PendingIntent =
        PendingIntent.getBroadcast(context, REQUEST, alarmIntent(context), FLAGS or extra)

    // 平台签名把 getBroadcast 标成非空，但带 FLAG_NO_CREATE 时运行期确实会返回 null
    @Suppress("USELESS_ELVIS")
    fun cancel(context: Context) {
        val alarm = pendingIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(alarm)
        alarm.cancel()
    }

    /**
     * 重排下一次检查：取所有启用规则里最早的到期时刻；没有启用规则就取消闹钟。
     * 开机、换时区、增删规则、执行完一轮之后都要调一次。
     */
    suspend fun reschedule(context: Context) {
        val rules = AppDatabase.getDatabase(context).recurringRecordDao().getEnabled()
        if (rules.isEmpty()) {
            cancel(context)
            return
        }
        val now = System.currentTimeMillis()
        val next = rules.minOf { RecurringSchedule.nextCheckAt(it.lastExecutedDate, it.frequency.days, now) }
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, next, pendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }
}

/**
 * 闹钟到点、开机、应用更新、改时间/时区时触发：先补记到期账单，再重排下一次闹钟。
 * 处理放在 goAsync + IO 协程里，避免阻塞主线程；异常只记日志，绝不让广播崩掉进程。
 */
class RecurringAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                try {
                    RecurringExecutor.run(app)
                } catch (e: Exception) {
                    Log.w(RecurringAlarm.TAG, "周期记账补记失败", e)
                }
                try {
                    RecurringAlarm.reschedule(app)
                } catch (e: Exception) {
                    Log.w(RecurringAlarm.TAG, "周期记账重排失败", e)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            RecurringAlarm.ACTION_CHECK,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
