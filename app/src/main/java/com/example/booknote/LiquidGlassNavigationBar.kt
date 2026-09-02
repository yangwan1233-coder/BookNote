package com.example.booknote

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import kotlin.math.abs

data class BottomNavItem(
    val route: String,
    val title: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector
)

// ==========================================
// 🌟 共享的三层液态玻璃结构（私有）
// 完全照搬 LiquidGlassNavigationBar 里已验证过没有覆盖问题的三层写法：
// 第 1 层玻璃背板 / 第 2 层指示块(可选) / 第 3 层内容，各自显式 zIndex(0f/1f/2f)。
// LiquidGlassNavigationBar 和 LiquidGlassSingleButton 都只是这个结构的两种不同用法。
// ==========================================
@Composable
private fun LiquidGlassLayeredSurface(
    width: Dp,
    height: Dp,
    hazeState: HazeState,
    blurRadius: Dp,
    borderAlphaBoost: Float,
    indicatorModifier: Modifier? = null,
    contentModifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
    ) {
        // 第 1 层：玻璃背板，只负责模糊 / 描边，不放任何内容
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .zIndex(0f)
                .graphicsLayer {
                    shape = CircleShape
                }
                .clip(CircleShape)
                .hazeChild(
                    state = hazeState,
                    style = HazeStyle(
                        blurRadius = blurRadius,
                        backgroundColor = Color.Transparent, // 必须补上背景色，防止运行时闪退
                        tint = dev.chrisbanes.haze.HazeTint(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = (0.6f * borderAlphaBoost).coerceAtMost(1f)),
                            Color.White.copy(alpha = 0.05f),
                            Color.White.copy(alpha = (0.2f * borderAlphaBoost).coerceAtMost(1f))
                        )
                    ),
                    shape = CircleShape
                )
        )

        // 第 2 层：液态指示块（可选，由调用方决定是否需要、以及位置/宽度）
        if (indicatorModifier != null) {
            Box(
                modifier = indicatorModifier
                    .zIndex(1f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        }

        // 第 3 层：内容层（图标 / 图标 Row 等），永远盖在最上面
        Box(
            modifier = contentModifier
                .width(width)
                .height(height)
                .zIndex(2f),
            contentAlignment = contentAlignment,
            content = content
        )
    }
}

@Composable
fun LiquidGlassNavigationBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onAddClick: () -> Unit,
    hazeState: HazeState
) {
    val totalSlots = items.size + 1
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    val currentRouteState by rememberUpdatedState(currentRoute)
    val onNavigateState by rememberUpdatedState(onNavigate)
    val onAddClickState by rememberUpdatedState(onAddClick)

    var touchX by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var tabWidthPx by remember { mutableFloatStateOf(0f) }

    val targetOffset = if (isDragging && tabWidthPx > 0f) {
        (touchX / tabWidthPx - 0.5f).coerceIn(0f, (totalSlots - 1).toFloat())
    } else {
        selectedIndex.toFloat()
    }
    val animatedOffset by animateFloatAsState(
        targetValue = targetOffset,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "liquid_indicator_offset"
    )

    val liftProgress by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "lift_progress"
    )

    val baseWidth = 220.dp
    val baseHeight = 56.dp
    val barWidth = baseWidth * (1f + 0.10f * liftProgress)
    val barHeight = baseHeight * (1f + 0.10f * liftProgress)

    val shadowSize = 16f + 8f * liftProgress

    val indicatorPadding = (6f - 1.2f * liftProgress).dp
    // 🌟 按住不放时额外放大指示块，幅度比上一版更明显
    val indicatorScale = 1f + 0.35f * liftProgress

    val blurRadius = (12 - 6 * liftProgress).dp
    val borderAlphaBoost = 1f + 0.3f * liftProgress

    val tabWidth = barWidth / totalSlots

    Box(

        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars) // 1. 让出系统小白条高度
            .padding(bottom = 10.dp)
    ) {
        LiquidGlassLayeredSurface(
            width = barWidth,
            height = barHeight,
            hazeState = hazeState,
            blurRadius = blurRadius,
            borderAlphaBoost = borderAlphaBoost,
            // 液态焦点指示块——画在玻璃背板之上，颜色不会被蒙白雾遮暗；
            // 没有外层 clip 约束，按住放大时可以真正溢出导航栏边界
            indicatorModifier = Modifier
                .offset(x = tabWidth * animatedOffset)
                .width(tabWidth)
                .height(barHeight)
                .padding(indicatorPadding)
                .graphicsLayer {
                    scaleX = indicatorScale
                    scaleY = indicatorScale
                },
            // 图标 Row 的手势也挂在内容层
            contentModifier = Modifier
                .onGloballyPositioned { tabWidthPx = it.size.width.toFloat() / totalSlots }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        isDragging = true
                        touchX = down.position.x

                        try {
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull()
                                if (change != null) {
                                    touchX = change.position.x
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })

                            if (tabWidthPx > 0f) {
                                val finalSlot = (touchX / tabWidthPx).toInt().coerceIn(0, totalSlots - 1)
                                if (finalSlot < items.size) {
                                    if (currentRouteState != items[finalSlot].route) {
                                        onNavigateState(items[finalSlot].route)
                                    }
                                } else {
                                    onAddClickState()
                                }
                            }
                        } finally {
                            isDragging = false
                        }
                    }
                }
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                items.forEachIndexed { index, item ->
                    val isSelected = currentRoute == item.route

                    val color by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(300), label = "icon_color"
                    )

                    val baseScale = if (isSelected) 1.2f else 1.0f
                    val targetScale = if (isDragging && tabWidthPx > 0f) {
                        val itemCenterX = (index + 0.5f) * tabWidthPx
                        val distance = abs(touchX - itemCenterX)
                        val maxDist = tabWidthPx * 1.5f
                        val normalized = (1f - distance / maxDist).coerceIn(0f, 1f)
                        1.0f + 0.2f * normalized
                    } else {
                        baseScale
                    }

                    val scale by animateFloatAsState(
                        targetValue = targetScale,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
                        label = "icon_scale"
                    )

                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.activeIcon else item.inactiveIcon,
                            contentDescription = item.title,
                            tint = color,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                        )
                    }
                }

                val addIndex = totalSlots - 1
                val targetAddScale = if (isDragging && tabWidthPx > 0f) {
                    val itemCenterX = (addIndex + 0.5f) * tabWidthPx
                    val distance = abs(touchX - itemCenterX)
                    val maxDist = tabWidthPx * 1.5f
                    val normalized = (1f - distance / maxDist).coerceIn(0f, 1f)
                    1.0f + 0.2f * normalized
                } else {
                    1.0f
                }

                val addScale by animateFloatAsState(
                    targetValue = targetAddScale,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "add_scale"
                )

                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "新建笔记",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                scaleX = addScale
                                scaleY = addScale
                            }
                    )
                }
            }
        }
    }
}
// ==========================================
// 🌟 纯图标液态玻璃单体按钮 (100% 同款主页参数版)
// ==========================================
@Composable
fun LiquidGlassSingleButton(
    baseWidth: androidx.compose.ui.unit.Dp,
    baseHeight: androidx.compose.ui.unit.Dp = 48.dp,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isIndicatorStyle: Boolean = false,
    iconTint: androidx.compose.ui.graphics.Color? = null,
    badgeCount: Int = 0,
    hazeState: dev.chrisbanes.haze.HazeState
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val liftProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "lift_progress"
    )

    // 🌟 完全照搬主页导航栏的缩放公式！
    val barWidth = baseWidth * (1f + 0.10f * liftProgress)
    val barHeight = baseHeight * (1f + 0.10f * liftProgress)
    val indicatorPadding = (8f - 1.2f * liftProgress).dp
    val indicatorScale = 1f + 0.3f * liftProgress
    val blurRadius = (12 - 6 * liftProgress).dp
    val borderAlphaBoost = 1f + 0.3f * liftProgress

    val iconScale by animateFloatAsState(
        targetValue = if (isPressed) 1.2f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "icon_scale"
    )

    // 复用和导航栏完全同一份三层结构：背板 / 指示块(可选) / 内容(图标+角标)
    LiquidGlassLayeredSurface(
        width = barWidth,
        height = barHeight,
        hazeState = hazeState,
        blurRadius = blurRadius,
        borderAlphaBoost = borderAlphaBoost,
        indicatorModifier = if (isIndicatorStyle) {
            Modifier
                .width(barWidth)
                .height(barHeight)
                .padding(indicatorPadding)
                .graphicsLayer {
                    scaleX = indicatorScale
                    scaleY = indicatorScale
                }
        } else {
            null
        },
        contentModifier = Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    ) {
        val defaultTint = if (isIndicatorStyle) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        Icon(
            imageVector = icon,
            contentDescription = "按钮",
            tint = iconTint ?: defaultTint,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )

        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 12.dp, y = (-12).dp)
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeCount.toString(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }
    }
}