package com.pixel.moneytracker.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.moneytracker.ui.theme.*

/**
 * 像素风卡片（轻量 3D 边框效果）
 */
@Composable
fun PixelCard(
    modifier: Modifier = Modifier,
    borderColor: Color = PixelSurface,
    backgroundColor: Color = PixelCardBg,
    borderWidth: Dp = 2.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            // 3D 效果：上左亮边 + 下右暗边
            .border(borderWidth, PixelHighlight.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(2.dp))
            .background(backgroundColor)
            .padding(12.dp),
        content = content
    )
}

/**
 * 像素风按钮（3D 按压效果）
 */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bgColor: Color = PixelGold,
    textColor: Color = PixelDarkBg,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "btn_scale"
    )

    // 按下时 3D 效果反转
    val topBorderColor = if (isPressed) PixelShadow else PixelHighlight

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, topBorderColor.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
            .background(if (enabled) bgColor else PixelTextDark)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true),
                enabled = enabled
            ) { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) textColor else PixelTextGray,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 2.sp
        )
    }
}

/**
 * 像素风标签
 */
@Composable
fun PixelTag(
    text: String,
    color: Color = PixelBlue,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/**
 * 像素风分割线
 */
@Composable
fun PixelDivider(
    modifier: Modifier = Modifier,
    color: Color = PixelSurface.copy(alpha = 0.5f),
    thickness: Dp = 1.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color)
    )
}

/**
 * 分类方格按钮（像素风选中效果）
 */
@Composable
fun CategoryChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.03f else if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chip_scale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) PixelGold.copy(alpha = 0.25f) else PixelDarkBg,
        animationSpec = tween(150),
        label = "chip_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) PixelGold else PixelSurface,
        animationSpec = tween(150),
        label = "chip_border"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) PixelGold else PixelTextGray,
        animationSpec = tween(150),
        label = "chip_text"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(2.dp))
            .border(
                if (selected) 2.dp else 1.dp,
                borderColor,
                RoundedCornerShape(2.dp)
            )
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true)
            ) { onClick() }
            .padding(vertical = 8.dp, horizontal = 6.dp)
    ) {
        val categoryIcon = CategoryIcons.getByName(name)
        Icon(
            imageVector = categoryIcon.icon,
            contentDescription = name,
            tint = categoryIcon.color,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = name,
            fontSize = 10.sp,
            color = textColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 像素风数字键盘（3D 按键效果）
 */
@Composable
fun PixelNumPad(
    onNumber: (String) -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        listOf("7", "8", "9"),
        listOf("4", "5", "6"),
        listOf("1", "2", "3"),
        listOf(".", "0", "DEL"),
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { key ->
                    Box(modifier = Modifier.weight(1f)) {
                        NumKey(
                            key = key,
                            isDelete = key == "DEL",
                            onClick = {
                                when (key) {
                                    "DEL" -> onDelete()
                                    else -> onNumber(key)
                                }
                            }
                        )
                    }
                }
            }
        }
        // 确认按钮
        val confirmInteraction = remember { MutableInteractionSource() }
        val confirmPressed by confirmInteraction.collectIsPressedAsState()
        val confirmScale by animateFloatAsState(
            targetValue = if (confirmPressed) 0.95f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessHigh
            ),
            label = "confirm_scale"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .scale(confirmScale)
                .clip(RoundedCornerShape(2.dp))
                .border(2.dp, PixelGold, RoundedCornerShape(2.dp))
                .background(PixelGold)
                .clickable(
                    interactionSource = confirmInteraction,
                    indication = rememberRipple(bounded = true)
                ) { onConfirm() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "确  认",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = PixelDarkBg,
                letterSpacing = 4.sp
            )
        }
    }
}

@Composable
private fun NumKey(
    key: String,
    isDelete: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "numkey_scale"
    )

    val bg by animateColorAsState(
        targetValue = if (isDelete) PixelRed.copy(alpha = if (isPressed) 1f else 0.8f)
                       else if (isPressed) PixelSurface.copy(alpha = 0.6f) else PixelSurface,
        animationSpec = tween(100),
        label = "numkey_bg"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .scale(scale)
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, PixelTextDark.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true)
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isDelete) "⌫" else key,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PixelTextWhite
        )
    }
}
