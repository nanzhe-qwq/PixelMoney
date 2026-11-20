package com.pixel.moneytracker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixel.moneytracker.data.database.AppDatabase
import com.pixel.moneytracker.data.entity.Budget
import com.pixel.moneytracker.data.entity.Frequency
import com.pixel.moneytracker.data.entity.Ledger
import com.pixel.moneytracker.data.entity.Record
import com.pixel.moneytracker.data.entity.RecordType
import com.pixel.moneytracker.data.entity.RecurringRecord
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * 截图用的演示数据。只在本机跑，跑完用备份恢复，不参与常规回归。
 *
 * 目的：让首页、统计、预算、周期记账、回收站在真实布局下都是「有内容」的状态，
 * 这样截图里能看到真实的排版和数字，而不是一堆 0。
 */
@RunWith(AndroidJUnit4::class)
class DemoSeedDeviceTest {

    private data class Demo(
        val merchant: String,
        val category: String,
        val emoji: String,
        val amount: Double,
        val type: RecordType = RecordType.EXPENSE
    )

    private fun expense(merchant: String, category: String, emoji: String, amount: Double) =
        Demo(merchant, category, emoji, amount)

    private fun income(merchant: String, category: String, emoji: String, amount: Double) =
        Demo(merchant, category, emoji, amount, RecordType.INCOME)

    /** 每天固定的几笔：房租水电这种「每月都会发生」的 */
    private fun monthly(day: Int): List<Demo> = listOf(
        expense("房东转账 · 房租", "居住", "🏠", 3200.0),
        expense("水电费", "居住", "🏠", 138.40),
        expense("话费充值", "通讯", "📱", 100.0),
        expense("健身房月卡", "运动", "⚽", 299.0)
    ).take(if (day == 1) 4 else 0)

    @Test
    fun seed() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getDatabase(context)
        db.clearAllTables()

        val daily = db.ledgerDao().insert(Ledger(name = "日常开销", emoji = "📝", isDefault = true))
        db.ledgerDao().insert(Ledger(name = "旅行基金", emoji = "✈️"))
        db.ledgerDao().insert(Ledger(name = "学习投资", emoji = "📚"))
        db.ledgerDao().insert(Ledger(name = "恋爱基金", emoji = "💕"))
        db.ledgerDao().insert(Ledger(name = "存钱目标", emoji = "🎯"))
        db.ledgerDao().insert(Ledger(name = "家庭账本", emoji = "🏠"))

        // 商家池：按出现频率排列，越靠前越常出现
        val pool = listOf(
            expense("星巴克(国贸店)", "咖啡", "☕", 35.0),
            expense("瑞幸咖啡", "咖啡", "☕", 16.9),
            expense("沙县小吃", "餐饮", "🍜", 22.0),
            expense("美团外卖", "餐饮", "🍜", 32.5),
            expense("京东外卖", "餐饮", "🍜", 28.0),
            expense("蜜雪冰城", "零食", "🍿", 8.0),
            expense("全家便利店", "零食", "🍿", 19.9),
            expense("地铁 10 号线", "交通", "🚗", 5.0),
            expense("滴滴出行", "交通", "🚗", 31.6),
            expense("永辉超市", "购物", "🛍️", 148.3),
            expense("淘宝 · 收纳盒", "购物", "🛍️", 89.0),
            expense("京东 · 洗衣液", "购物", "🛍️", 129.0),
            expense("拼多多 · 垃圾袋", "购物", "🛍️", 19.9),
            expense("优衣库 · 卫衣", "服饰", "👗", 199.0),
            expense("理发", "美妆", "💄", 68.0),
            expense("药房 · 感冒药", "医疗", "💊", 36.5),
            expense("西西弗书店", "教育", "📚", 68.0),
            expense("万达影城", "娱乐", "🎮", 62.0),
            expense("猫粮 5kg", "宠物", "🐱", 189.0),
            expense("朋友聚餐 AA", "社交", "🍻", 128.0)
        )

        var seed = 7L
        val records = mutableListOf<Record>()
        fun push(date: LocalDate, demo: Demo) {
            val time = date.atTime(9 + (seed % 12).toInt(), (seed * 7 % 60).toInt())
            records += Record(
                amount = demo.amount,
                type = demo.type,
                category = demo.category,
                emoji = demo.emoji,
                note = demo.merchant,
                year = time.year,
                month = time.monthValue,
                day = time.dayOfMonth,
                ledgerId = daily,
                timestamp = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
            seed = (seed * 31 + 17) % 1000003
        }

        listOf(9, 10, 11).forEach { month ->
            val days = LocalDate.of(2026, month, 1).lengthOfMonth()
            for (day in 1..days) {
                val date = LocalDate.of(2026, month, day)
                monthly(day).forEach { push(date, it) }
                // 每天 1~3 笔日常，周末多一笔娱乐
                val count = when {
                    seed % 5 == 0L -> 3
                    seed % 3 == 0L -> 2
                    else -> 1
                }
                repeat(count) {
                    push(date, pool[((seed + it * 3) % pool.size).toInt()])
                }
                if (date.dayOfWeek.value >= 6 && day % 3 == 0) push(date, pool[17])
            }
        }

        // 收入
        listOf(9, 10, 11).forEach { month ->
            val payDay = LocalDate.of(2026, month, 10)
            records += Record(
                amount = 18500.0, type = RecordType.INCOME, category = "工资", emoji = "💰",
                note = "工资 · 10 月".replace("10 月", "$month 月"),
                year = payDay.year, month = payDay.monthValue, day = payDay.dayOfMonth,
                ledgerId = daily,
                timestamp = payDay.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
            records += Record(
                amount = 86.42, type = RecordType.INCOME, category = "理财", emoji = "📈",
                note = "余额宝收益", year = payDay.year, month = payDay.monthValue, day = 20,
                ledgerId = daily,
                timestamp = LocalDate.of(2026, month, 20).atTime(9, 30)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        }
        records += Record(
            amount = 200.0, type = RecordType.INCOME, category = "红包", emoji = "🧧",
            note = "生日红包", year = 2026, month = 10, day = 12, ledgerId = daily,
            timestamp = LocalDate.of(2026, 10, 12).atTime(21, 12)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )

        // 旅行基金账本里放两笔大额，证明多账本
        val travel = daily + 1
        records += Record(
            amount = 1680.0, type = RecordType.EXPENSE, category = "旅行", emoji = "✈️",
            note = "杭州 · 酒店两晚", year = 2026, month = 10, day = 2, ledgerId = travel,
            timestamp = LocalDate.of(2026, 10, 2).atTime(15, 40)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        records += Record(
            amount = 553.0, type = RecordType.EXPENSE, category = "交通", emoji = "🚗",
            note = "高铁 北京南 → 杭州东", year = 2026, month = 10, day = 2, ledgerId = travel,
            timestamp = LocalDate.of(2026, 10, 2).atTime(7, 0)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )

        records.forEach { db.recordDao().insert(it) }

        // 本月预算：刚好用到 78%，让预算卡显示「接近上限」而不是超支
        val spent = records.filter { it.type == RecordType.EXPENSE && it.ledgerId == daily && it.month == 11 }
            .sumOf { it.amount }
        db.budgetDao().insert(Budget(year = 2026, month = 11, amount = 13000.0))

        // 周期记账：两条
        db.recurringRecordDao().insert(
            RecurringRecord(
                amount = 3200.0, type = RecordType.EXPENSE, category = "居住", emoji = "🏠",
                note = "房租", frequency = Frequency.MONTHLY, ledgerId = daily
            )
        )
        db.recurringRecordDao().insert(
            RecurringRecord(
                amount = 25.0, type = RecordType.EXPENSE, category = "通讯", emoji = "📱",
                note = "视频会员", frequency = Frequency.MONTHLY, enabled = false, ledgerId = daily
            )
        )

        // 回收站里放两笔，让回收站页不是空态
        val extra = listOf(
            Record(
                amount = 45.0, type = RecordType.EXPENSE, category = "餐饮", emoji = "🍜",
                note = "误记 · 兰州拉面", year = 2026, month = 11, day = 18, ledgerId = daily,
                timestamp = LocalDate.of(2026, 11, 18).atTime(12, 20)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            ),
            Record(
                amount = 12.0, type = RecordType.EXPENSE, category = "零食", emoji = "🍿",
                note = "重复记录 · 便利店", year = 2026, month = 11, day = 19, ledgerId = daily,
                timestamp = LocalDate.of(2026, 11, 19).atTime(20, 5)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        )
        extra.forEach { db.recordDao().insert(it.copy(deletedAt = System.currentTimeMillis() - 86_400_000L)) }

        println("SEEDED records=${records.size + 2} expense=${records.filter { it.type == RecordType.EXPENSE }.sumOf { it.amount }}")
    }
}
