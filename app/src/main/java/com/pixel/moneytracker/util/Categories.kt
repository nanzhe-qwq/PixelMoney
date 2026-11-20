package com.pixel.moneytracker.util

import com.pixel.moneytracker.data.entity.RecordType

/**
 * 预设分类（像素风 emoji）
 */
object Categories {

    data class Category(
        val name: String,
        val emoji: String,
        val type: RecordType
    )

    val expenseCategories = listOf(
        Category("餐饮", "🍜", RecordType.EXPENSE),
        Category("交通", "🚗", RecordType.EXPENSE),
        Category("购物", "🛍️", RecordType.EXPENSE),
        Category("娱乐", "🎮", RecordType.EXPENSE),
        Category("居住", "🏠", RecordType.EXPENSE),
        Category("医疗", "💊", RecordType.EXPENSE),
        Category("教育", "📚", RecordType.EXPENSE),
        Category("通讯", "📱", RecordType.EXPENSE),
        Category("服饰", "👗", RecordType.EXPENSE),
        Category("美妆", "💄", RecordType.EXPENSE),
        Category("运动", "⚽", RecordType.EXPENSE),
        Category("社交", "🍻", RecordType.EXPENSE),
        Category("宠物", "🐱", RecordType.EXPENSE),
        Category("零食", "🍿", RecordType.EXPENSE),
        Category("咖啡", "☕", RecordType.EXPENSE),
        Category("数码", "💻", RecordType.EXPENSE),
        Category("礼物", "🎁", RecordType.EXPENSE),
        Category("旅行", "✈️", RecordType.EXPENSE),
        Category("其他", "📦", RecordType.EXPENSE),
    )

    val incomeCategories = listOf(
        Category("工资", "💰", RecordType.INCOME),
        Category("奖金", "🏆", RecordType.INCOME),
        Category("兼职", "💼", RecordType.INCOME),
        Category("理财", "📈", RecordType.INCOME),
        Category("红包", "🧧", RecordType.INCOME),
        Category("退款", "↩️", RecordType.INCOME),
        Category("中奖", "🎰", RecordType.INCOME),
        Category("其他", "✨", RecordType.INCOME),
    )

    fun getByType(type: RecordType) = if (type == RecordType.EXPENSE) expenseCategories else incomeCategories
}
