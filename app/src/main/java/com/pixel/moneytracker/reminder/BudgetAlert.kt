package com.pixel.moneytracker.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pixel.moneytracker.MainActivity
import com.pixel.moneytracker.R
import com.pixel.moneytracker.data.database.AppDatabase
import com.pixel.moneytracker.util.BudgetLevel
import com.pixel.moneytracker.util.BudgetPolicy
import com.pixel.moneytracker.util.Money

/**
 * 月度预算提醒。
 *
 * 去重口径：一条预算周期 = 一个自然月，记录在 SharedPreferences("budget_alert")
 * 的 `alert_<year>_<month>` 里，值是「已经提醒到的最高级别」（80 或 100）。
 * - 到 80% 提醒一次，之后继续记账不会重复弹；
 * - 继续花到 100% 再提醒一次（升级到更高档）；
 * - 同一档位在本月内不再重复；
 * - 下一自然月自动重新开始（键不同）。
 * 只有通知真的发出去之后才写水位，通知权限被关时不写，等用户开了权限再补一次。
 *
 * 金额口径：与首页进度条一致 —— 预算取 budgets 当月的记录，支出取**当前账本**当月的
 * 支出合计（App 里预算是全局的，账目是按账本分开的）。
 */
internal object BudgetAlert {

    const val OPEN_BUDGET = "com.pixel.moneytracker.action.OPEN_BUDGET"
    const val CHANNEL = "budget_alert"
    const val NOTICE_ID = 4302

    /** 判定与写水位之间的互斥，避免「记账 + 周期记账」同时触发时各弹一条。 */
    private val lock = Any()

    private fun prefs(c: Context) = c.getSharedPreferences("budget_alert", Context.MODE_PRIVATE)

    private fun key(year: Int, month: Int) = "alert_${year}_$month"

    fun notifiedPercent(c: Context, year: Int, month: Int): Int = prefs(c).getInt(key(year, month), 0)

    /**
     * 清掉某个月份的提醒水位。用户改了预算金额或删掉预算时调用：
     * 换了上限，原来的「已经提醒过 80%」就不再成立，应该按新上限重新判定。
     */
    fun reset(c: Context, year: Int, month: Int) {
        synchronized(lock) {
            prefs(c).edit().remove(key(year, month)).apply()
        }
    }

    /** 供界面展示「本月已提醒过 80% 那一次」这类状态。 */
    fun hasNotified(c: Context, year: Int, month: Int, level: BudgetLevel): Boolean =
        notifiedPercent(c, year, month) >= level.percent

    /**
     * 检查并（必要时）发提醒。注意：调用方负责把 [ledgerId] 传成「当前账本」。
     * @return 真正发出提醒的级别；没有提醒时返回 null
     */
    suspend fun evaluate(context: Context, ledgerId: Long, year: Int, month: Int): BudgetLevel? {
        val db = AppDatabase.getDatabase(context)
        val budget = db.budgetDao().getBudgetSync(year, month) ?: return null
        val spent = db.recordDao().getMonthExpenseByLedgerSync(ledgerId, year, month)
        val budgetCents = Money.toCents(budget.amount)
        val spentCents = Money.toCents(spent)
        val level = BudgetPolicy.level(budgetCents, spentCents) ?: return null
        synchronized(lock) {
            val notify = BudgetPolicy.shouldNotify(level, notifiedPercent(context, year, month)) ?: return null
            if (!post(context, notify, budgetCents, spentCents)) return null
            prefs(context).edit().putInt(key(year, month), notify.percent).apply()
            return notify
        }
    }

    fun createChannel(c: Context) {
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "预算提醒", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun canNotify(c: Context): Boolean {
        val manager = c.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun post(context: Context, level: BudgetLevel, budgetCents: Long, spentCents: Long): Boolean {
        createChannel(context)
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!canNotify(context)) return false

        val remaining = budgetCents - spentCents
        val title = when (level) {
            BudgetLevel.WARNING -> "本月预算已用 ${BudgetPolicy.usedPercent(budgetCents, spentCents)}%"
            BudgetLevel.EXCEEDED -> "本月预算已超支"
        }
        val text = when (level) {
            BudgetLevel.WARNING ->
                "已用 ¥${Money.format(spentCents)} / ¥${Money.format(budgetCents)}，还剩 ¥${Money.format(remaining)}。"
            BudgetLevel.EXCEEDED ->
                "已用 ¥${Money.format(spentCents)} / ¥${Money.format(budgetCents)}，超出 ¥${Money.format(-remaining)}。"
        }
        val open = Intent(context, MainActivity::class.java).setAction(OPEN_BUDGET)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(context, NOTICE_ID, open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        return try {
            context.getSystemService(NotificationManager::class.java).notify(NOTICE_ID, notification)
            true
        } catch (_: SecurityException) {
            // 权限可能在检查与 notify 之间被撤销。
            false
        }
    }
}
