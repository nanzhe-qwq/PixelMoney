package com.pixel.moneytracker.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * 像素风图标系统 — 12x12 手绘像素画
 * '#' = 主色, 'o' = 点缀色, '.' = 透明
 */
object PixelIcons {

    data class PixelArt(
        val grid: List<String>,
        val color: Color,
        val accent: Color = Color.White
    )

    // ====== 支出分类 ======

    // 🍜 餐饮 — 碗（简洁饱满）
    private val foodArt = PixelArt(
        grid = listOf(
            "............",
            "............",
            "............",
            ".##########.",
            "############",
            "############",
            "############",
            "############",
            ".##########.",
            "..########..",
            "...######...",
            "............"
        ),
        color = Color(0xFFFF6B6B),
        accent = Color(0xFFFFE66D)
    )

    // 🚗 交通 — 车（方正轮廓）
    private val transportArt = PixelArt(
        grid = listOf(
            "............",
            "............",
            "...######...",
            "..########..",
            ".##########.",
            "############",
            "############",
            "############",
            ".##.####.##.",
            "..##....##..",
            "............",
            "............"
        ),
        color = Color(0xFF4ECDC4),
        accent = Color(0xFFA8E6CF)
    )

    // 🛍️ 购物 — 袋子
    private val shoppingArt = PixelArt(
        grid = listOf(
            "....##......",
            "...#..#.....",
            "..#....#....",
            ".##########.",
            ".#........#.",
            ".#........#.",
            ".#........#.",
            ".#........#.",
            ".##########.",
            "............",
            "............",
            "............"
        ),
        color = Color(0xFFFF9FF3),
        accent = Color(0xFFF368E0)
    )

    // 🎮 娱乐 — 手柄
    private val gameArt = PixelArt(
        grid = listOf(
            "............",
            "..##....##..",
            ".####..####.",
            ".##########.",
            ".####..####.",
            ".##.#..#.##.",
            ".##.#..#.##.",
            "..########..",
            "...######...",
            "............",
            "............",
            "............"
        ),
        color = Color(0xFFA29BFE),
        accent = Color(0xFF6C5CE7)
    )

    // 🏠 居住 — 房子
    private val houseArt = PixelArt(
        grid = listOf(
            ".....##.....",
            "....####....",
            "...######...",
            "..########..",
            ".#.#....#.#.",
            ".#.#....#.#.",
            ".#.######.#.",
            ".#........#.",
            ".##########.",
            "............",
            "............",
            "............"
        ),
        color = Color(0xFFFDCB6E),
        accent = Color(0xFFF9CA24)
    )

    // 💊 医疗 — 十字
    private val medicalArt = PixelArt(
        grid = listOf(
            "............",
            "....##......",
            "....##......",
            ".##########.",
            ".##########.",
            ".##########.",
            ".##########.",
            "....##......",
            "....##......",
            "............",
            "............",
            "............"
        ),
        color = Color(0xFFFF6B6B),
        accent = Color(0xFFFFFFFF)
    )

    // 📚 教育 — 书
    private val bookArt = PixelArt(
        grid = listOf(
            "............",
            ".##########.",
            ".#.#.#.#.#..",
            ".#.#.#.#.#..",
            ".#.#.#.#.#..",
            ".#.#.#.#.#..",
            ".#.#.#.#.#..",
            ".#.#.#.#.#..",
            ".##########.",
            "............",
            "............",
            "............"
        ),
        color = Color(0xFF74B9FF),
        accent = Color(0xFFDFE6E9)
    )

    // 📱 通讯 — 手机
    private val phoneArt = PixelArt(
        grid = listOf(
            "..########..",
            ".#........#.",
            ".#........#.",
            ".#........#.",
            ".#........#.",
            ".#........#.",
            ".#........#.",
            ".#...##...#.",
            "..###..###..",
            "...######...",
            "............",
            "............"
        ),
        color = Color(0xFF636E72),
        accent = Color(0xFF74B9FF)
    )

    // 👗 服饰 — 衣服
    private val dressArt = PixelArt(
        grid = listOf(
            "............",
            ".##......##.",
            "..##....##..",
            "..########..",
            ".##########.",
            ".##########.",
            ".##########.",
            ".##########.",
            ".##########.",
            "............",
            "............",
            "............"
        ),
        color = Color(0xFFE056A0),
        accent = Color(0xFFF8A5C2)
    )

    // 💄 美妆 — 口红
    private val makeupArt = PixelArt(
        grid = listOf(
            "....##......",
            "....##......",
            "...####.....",
            "...####.....",
            "..######....",
            "..######....",
            ".########...",
            ".########...",
            ".########...",
            "............",
            "............",
            "............"
        ),
        color = Color(0xFFE056A0),
        accent = Color(0xFFFAD390)
    )

    // ⚽ 运动 — 球
    private val sportArt = PixelArt(
        grid = listOf(
            "............",
            "...######...",
            "..##.##.##..",
            ".##.#.##.#..",
            ".#..#.##.#..",
            ".##.#.##.#..",
            ".#..#.##.#..",
            ".##.#.##.#..",
            "..##.##.##..",
            "...######...",
            "............",
            "............"
        ),
        color = Color(0xFF00B894),
        accent = Color(0xFF55EFC4)
    )

    // 🍻 社交 — 杯子
    private val socialArt = PixelArt(
        grid = listOf(
            "............",
            ".##......##.",
            ".##......##.",
            "..##....##..",
            "..##....##..",
            "...##..##...",
            "...######...",
            "....####....",
            "...######...",
            "..########..",
            "............",
            "............"
        ),
        color = Color(0xFFFECA57),
        accent = Color(0xFFFF9FF3)
    )

    // 🐱 宠物 — 猫脸
    private val petArt = PixelArt(
        grid = listOf(
            "............",
            ".##......##.",
            ".###....###.",
            ".#.######.#.",
            ".#.######.#.",
            ".#..#..#..#.",
            ".#........#.",
            "..########..",
            "...######...",
            "............",
            "............",
            "............"
        ),
        color = Color(0xFFFDCB6E),
        accent = Color(0xFFE17055)
    )

    // 🍿 零食 — 爆米花
    private val snackArt = PixelArt(
        grid = listOf(
            "............",
            ".o.o.o.o.o..",
            ".#.#.#.#.#..",
            ".#.#.#.#.#..",
            "..########..",
            "...######...",
            "...######...",
            "...######...",
            "....####....",
            "............",
            "............",
            "............"
        ),
        color = Color(0xFFE17055),
        accent = Color(0xFFFDCB6E)
    )

    // ☕ 咖啡 — 杯子
    private val coffeeArt = PixelArt(
        grid = listOf(
            "............",
            "...o..o.....",
            "............",
            ".##########.",
            ".#........#.",
            ".#........#.",
            ".#........#.",
            "..########..",
            "...######...",
            "....####....",
            "............",
            "............"
        ),
        color = Color(0xFF6C5B3E),
        accent = Color(0xFFFDCB6E)
    )

    // 💻 数码 — 电脑
    private val digitalArt = PixelArt(
        grid = listOf(
            ".##########.",
            ".#........#.",
            ".#........#.",
            ".#........#.",
            ".#........#.",
            ".#........#.",
            ".##########.",
            "############",
            "############",
            "............",
            "............",
            "............"
        ),
        color = Color(0xFF74B9FF),
        accent = Color(0xFFA8E6CF)
    )

    // 🎁 礼物 — 礼盒
    private val giftArt = PixelArt(
        grid = listOf(
            "............",
            "....##......",
            "...####.....",
            ".##########.",
            ".#.##.##.#..",
            ".#.##.##.#..",
            ".##########.",
            ".#.##.##.#..",
            ".#.##.##.#..",
            ".##########.",
            "............",
            "............"
        ),
        color = Color(0xFFFF6B6B),
        accent = Color(0xFFFECA57)
    )

    // ✈️ 旅行 — 飞机
    private val travelArt = PixelArt(
        grid = listOf(
            "............",
            "............",
            "...##.......",
            "..####......",
            ".######.....",
            "##########..",
            ".######.....",
            "..####......",
            "...##.......",
            "............",
            "............",
            "............"
        ),
        color = Color(0xFF00CEC9),
        accent = Color(0xFF81ECEC)
    )

    // 📦 其他 — 箱子
    private val boxArt = PixelArt(
        grid = listOf(
            "............",
            ".##########.",
            ".#..##..##..",
            ".#..##..##..",
            ".##########.",
            ".#..##..##..",
            ".#..##..##..",
            ".##########.",
            "............",
            "............",
            "............",
            "............"
        ),
        color = Color(0xFF636E72),
        accent = Color(0xFFB2BEC3)
    )

    // ====== 收入分类 ======

    // 💰 工资 — 钱袋
    private val moneyArt = PixelArt(
        grid = listOf(
            "............",
            "...######...",
            "..########..",
            ".##########.",
            ".##.####.##.",
            ".##.####.##.",
            ".##########.",
            "..########..",
            "...######...",
            "............",
            "............",
            "............"
        ),
        color = Color(0xFF00B894),
        accent = Color(0xFF55EFC4)
    )

    // 🏆 奖金 — 奖杯
    private val trophyArt = PixelArt(
        grid = listOf(
            ".##......##.",
            ".##......##.",
            ".#.######.#.",
            "..########..",
            "...######...",
            "....####....",
            "...######...",
            "..########..",
            ".##########.",
            "............",
            "............",
            "............"
        ),
        color = Color(0xFFFECA57),
        accent = Color(0xFFFF9FF3)
    )

    // 💼 兼职 — 公文包
    private val briefcaseArt = PixelArt(
        grid = listOf(
            "............",
            "...####.....",
            "..#....#....",
            ".##########.",
            "#..........#",
            "##########.#",
            "#.#......#.#",
            "##########.#",
            "#..........#",
            ".##########.",
            "............",
            "............"
        ),
        color = Color(0xFF6C5B3E),
        accent = Color(0xFFFDCB6E)
    )

    // 📈 理财 — 图表
    private val chartArt = PixelArt(
        grid = listOf(
            "............",
            ".........#..",
            ".......#.#..",
            ".....#..#...",
            "...#...#....",
            ".#....#.....",
            "#...#.......",
            "............",
            "............",
            "............",
            "............",
            "............"
        ),
        color = Color(0xFF00B894),
        accent = Color(0xFF55EFC4)
    )

    // 🧧 红包
    private val redPacketArt = PixelArt(
        grid = listOf(
            "............",
            ".##########.",
            "##........##",
            "#..######..#",
            "#..#....#..#",
            "#..######..#",
            "#..#....#..#",
            "##........##",
            ".##########.",
            "............",
            "............",
            "............"
        ),
        color = Color(0xFFE17055),
        accent = Color(0xFFFECA57)
    )

    // ↩️ 退款 — 箭头
    private val refundArt = PixelArt(
        grid = listOf(
            "............",
            "...##.......",
            "..#.........",
            ".#..........",
            ".##########.",
            ".#..........",
            "..#.........",
            "...##.......",
            "............",
            "............",
            "............",
            "............"
        ),
        color = Color(0xFF74B9FF),
        accent = Color(0xFFDFE6E9)
    )

    // 🎰 中奖 — 四叶草
    private val lotteryArt = PixelArt(
        grid = listOf(
            "............",
            "...####.....",
            "..######....",
            ".##....##...",
            ".##....##...",
            ".##....##...",
            "..######....",
            "...####.....",
            "............",
            "............",
            "............",
            "............"
        ),
        color = Color(0xFF00B894),
        accent = Color(0xFF55EFC4)
    )

    // ✨ 其他收入 — 闪光
    private val sparkleArt = PixelArt(
        grid = listOf(
            "............",
            "....##......",
            "....##......",
            "............",
            ".##....##...",
            "............",
            "....##......",
            "....##......",
            "............",
            "............",
            "............",
            "............"
        ),
        color = Color(0xFFFECA57),
        accent = Color(0xFFFFFFFF)
    )

    fun getByName(name: String): PixelArt = when (name) {
        "餐饮" -> foodArt
        "交通" -> transportArt
        "购物" -> shoppingArt
        "娱乐" -> gameArt
        "居住" -> houseArt
        "医疗" -> medicalArt
        "教育" -> bookArt
        "通讯" -> phoneArt
        "服饰" -> dressArt
        "美妆" -> makeupArt
        "运动" -> sportArt
        "社交" -> socialArt
        "宠物" -> petArt
        "零食" -> snackArt
        "咖啡" -> coffeeArt
        "数码" -> digitalArt
        "礼物" -> giftArt
        "旅行" -> travelArt
        "其他" -> boxArt
        "工资" -> moneyArt
        "奖金" -> trophyArt
        "兼职" -> briefcaseArt
        "理财" -> chartArt
        "红包" -> redPacketArt
        "退款" -> refundArt
        "中奖" -> lotteryArt
        else -> sparkleArt
    }
}

@Composable
fun PixelIcon(
    art: PixelIcons.PixelArt,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val rows = art.grid.size
        val cols = art.grid.maxOf { it.length }
        val pixelSize = minOf(size.width / cols, size.height / rows)
        val offsetX = (size.width - pixelSize * cols) / 2
        val offsetY = (size.height - pixelSize * rows) / 2

        for (row in art.grid.indices) {
            for (col in art.grid[row].indices) {
                val ch = art.grid[row][col]
                if (ch == '#' || ch == 'o') {
                    drawRect(
                        color = if (ch == 'o') art.accent else art.color,
                        topLeft = Offset(
                            x = offsetX + col * pixelSize,
                            y = offsetY + row * pixelSize
                        ),
                        size = Size(pixelSize, pixelSize)
                    )
                }
            }
        }
    }
}
