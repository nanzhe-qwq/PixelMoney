package com.pixel.moneytracker.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 账本图标系统 — Material Icons 矢量图标
 * 像素风配色
 */
object LedgerIcons {

    data class LedgerIcon(
        val icon: ImageVector,
        val color: Color
    )

    // 账本图标映射
    private val dailyIcon = LedgerIcon(Icons.Default.Receipt, Color(0xFFFF6B6B))        // 📝 日常开销
    private val travelIcon = LedgerIcon(Icons.Default.Flight, Color(0xFF00CEC9))         // ✈️ 旅行基金
    private val studyIcon = LedgerIcon(Icons.AutoMirrored.Filled.MenuBook, Color(0xFF74B9FF))        // 📚 学习投资
    private val loveIcon = LedgerIcon(Icons.Default.Favorite, Color(0xFFE056A0))         // 💕 恋爱基金
    private val goalIcon = LedgerIcon(Icons.Default.Flag, Color(0xFFFECA57))             // 🎯 存钱目标
    private val homeIcon = LedgerIcon(Icons.Default.Home, Color(0xFFFDCB6E))             // 🏠 家庭账本
    private val workIcon = LedgerIcon(Icons.Default.Work, Color(0xFF6C5B3E))             // 💼 工作
    private val gameIcon = LedgerIcon(Icons.Default.SportsEsports, Color(0xFFA29BFE))    // 🎮 娱乐
    private val foodIcon = LedgerIcon(Icons.Default.Restaurant, Color(0xFFFF6B6B))       // 🍜 美食
    private val healthIcon = LedgerIcon(Icons.Default.LocalHospital, Color(0xFF00B894))  // 💊 健康
    private val giftIcon = LedgerIcon(Icons.Default.CardGiftcard, Color(0xFFFF9FF3))     // 🎁 礼物
    private val savingsIcon = LedgerIcon(Icons.Default.Savings, Color(0xFF00B894))       // 💰 存钱

    // emoji → 图标映射
    private val emojiMap = mapOf(
        "📝" to dailyIcon,
        "✈️" to travelIcon,
        "📚" to studyIcon,
        "💕" to loveIcon,
        "🎯" to goalIcon,
        "🏠" to homeIcon,
        "💼" to workIcon,
        "🎮" to gameIcon,
        "🍜" to foodIcon,
        "💊" to healthIcon,
        "🎁" to giftIcon,
        "💰" to savingsIcon
    )

    /**
     * 通过 emoji 获取图标
     */
    fun getByEmoji(emoji: String): LedgerIcon = emojiMap[emoji] ?: dailyIcon

    /**
     * 获取所有可用的 emoji 列表
     */
    fun allEmojis(): List<String> = emojiMap.keys.toList()
}
