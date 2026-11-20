package com.pixel.moneytracker.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val PixelColorScheme = darkColorScheme(
    primary = PixelGold,
    secondary = PixelBlue,
    tertiary = PixelPurple,
    background = PixelDarkBg,
    surface = PixelCardBg,
    onPrimary = PixelDarkBg,
    onSecondary = PixelTextWhite,
    onTertiary = PixelTextWhite,
    onBackground = PixelTextWhite,
    onSurface = PixelTextWhite,
    error = PixelRed,
    onError = PixelTextWhite,
    surfaceVariant = PixelSurface,
    onSurfaceVariant = PixelTextGray,
)

// 像素风文字样式
val PixelTitleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    letterSpacing = 2.sp,
    color = PixelGold
)

val PixelBodyStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    letterSpacing = 1.sp,
    color = PixelTextWhite
)

val PixelAmountStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    letterSpacing = 1.sp,
)

val PixelSmallStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    letterSpacing = 0.5.sp,
    color = PixelTextGray
)

// 像素风数字显示样式
val PixelDigitStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    letterSpacing = 2.sp,
    color = PixelGold
)

// 像素风标签样式
val PixelLabelStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 11.sp,
    letterSpacing = 2.sp,
    color = PixelTextGray
)

// 像素风按键文字样式
val PixelKeyStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 18.sp,
    letterSpacing = 0.sp,
    color = PixelTextWhite
)

@Composable
fun PixelMoneyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PixelColorScheme,
        typography = Typography(),
        content = content
    )
}
