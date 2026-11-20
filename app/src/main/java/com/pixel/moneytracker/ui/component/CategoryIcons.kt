package com.pixel.moneytracker.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 分类图标系统 — Material Icons 矢量图标
 * 清晰、好看、任何尺寸都完美
 */
object CategoryIcons {

    data class CategoryIcon(
        val icon: ImageVector,
        val color: Color
    )

    // ====== 支出分类 ======

    private val foodIcon = CategoryIcon(Icons.Default.Restaurant, Color(0xFFFF6B6B))
    private val transportIcon = CategoryIcon(Icons.Default.DirectionsCar, Color(0xFF4ECDC4))
    private val shoppingIcon = CategoryIcon(Icons.Default.ShoppingBag, Color(0xFFFF9FF3))
    private val gameIcon = CategoryIcon(Icons.Default.SportsEsports, Color(0xFFA29BFE))
    private val houseIcon = CategoryIcon(Icons.Default.Home, Color(0xFFFDCB6E))
    private val medicalIcon = CategoryIcon(Icons.Default.LocalHospital, Color(0xFFFF6B6B))
    private val bookIcon = CategoryIcon(Icons.AutoMirrored.Filled.MenuBook, Color(0xFF74B9FF))
    private val phoneIcon = CategoryIcon(Icons.Default.PhoneAndroid, Color(0xFF636E72))
    private val dressIcon = CategoryIcon(Icons.Default.Checkroom, Color(0xFFE056A0))
    private val makeupIcon = CategoryIcon(Icons.Default.Face, Color(0xFFE056A0))
    private val sportIcon = CategoryIcon(Icons.Default.FitnessCenter, Color(0xFF00B894))
    private val socialIcon = CategoryIcon(Icons.Default.WineBar, Color(0xFFFECA57))
    private val petIcon = CategoryIcon(Icons.Default.Pets, Color(0xFFFDCB6E))
    private val snackIcon = CategoryIcon(Icons.Default.Fastfood, Color(0xFFE17055))
    private val coffeeIcon = CategoryIcon(Icons.Default.Coffee, Color(0xFF6C5B3E))
    private val digitalIcon = CategoryIcon(Icons.Default.Devices, Color(0xFF74B9FF))
    private val giftIcon = CategoryIcon(Icons.Default.CardGiftcard, Color(0xFFFF6B6B))
    private val travelIcon = CategoryIcon(Icons.Default.Flight, Color(0xFF00CEC9))
    private val boxIcon = CategoryIcon(Icons.Default.Inventory2, Color(0xFF636E72))

    // ====== 收入分类 ======

    private val moneyIcon = CategoryIcon(Icons.Default.AttachMoney, Color(0xFF00B894))
    private val trophyIcon = CategoryIcon(Icons.Default.EmojiEvents, Color(0xFFFECA57))
    private val briefcaseIcon = CategoryIcon(Icons.Default.Work, Color(0xFF6C5B3E))
    private val chartIcon = CategoryIcon(Icons.AutoMirrored.Filled.TrendingUp, Color(0xFF00B894))
    private val redPacketIcon = CategoryIcon(Icons.Default.Redeem, Color(0xFFE17055))
    private val refundIcon = CategoryIcon(Icons.AutoMirrored.Filled.Undo, Color(0xFF74B9FF))
    private val lotteryIcon = CategoryIcon(Icons.Default.Star, Color(0xFFFECA57))
    private val sparkleIcon = CategoryIcon(Icons.Default.AutoAwesome, Color(0xFFFECA57))

    /**
     * 通过分类名称获取图标
     */
    fun getByName(name: String): CategoryIcon = when (name) {
        "餐饮" -> foodIcon
        "交通" -> transportIcon
        "购物" -> shoppingIcon
        "娱乐" -> gameIcon
        "居住" -> houseIcon
        "医疗" -> medicalIcon
        "教育" -> bookIcon
        "通讯" -> phoneIcon
        "服饰" -> dressIcon
        "美妆" -> makeupIcon
        "运动" -> sportIcon
        "社交" -> socialIcon
        "宠物" -> petIcon
        "零食" -> snackIcon
        "咖啡" -> coffeeIcon
        "数码" -> digitalIcon
        "礼物" -> giftIcon
        "旅行" -> travelIcon
        "其他" -> boxIcon
        "工资" -> moneyIcon
        "奖金" -> trophyIcon
        "兼职" -> briefcaseIcon
        "理财" -> chartIcon
        "红包" -> redPacketIcon
        "退款" -> refundIcon
        "中奖" -> lotteryIcon
        else -> sparkleIcon
    }
}
