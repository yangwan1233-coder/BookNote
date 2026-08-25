package com.example.booknote

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

data class BottomNavItem(
    val route: String,
    val title: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector
)

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

    val blurRadius = (20 + 8 * liftProgress).dp
    val borderAlphaBoost = 1f + 0.3f * liftProgress

    val tabWidth = barWidth / totalSlots

    Box(
        modifier = Modifier.padding(bottom = 32.dp)
    ) {
        // 第 1 层：玻璃背板，只负责模糊 / 描边 / 阴影，不放任何内容——
        // 指示块和图标都不再嵌套在它里面，这样指示块就不会被这层半透明白雾盖暗
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(barHeight)
                .graphicsLayer {
                    //shadowElevation = shadowSize.dp.toPx()
                    shape = CircleShape
                }
                .clip(CircleShape)
                .hazeChild(
                    state = hazeState,
                    shape = CircleShape,
                    style = dev.chrisbanes.haze.HazeStyle(
                        blurRadius = blurRadius,
                        tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
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

        // 第 2 层：液态焦点指示块——画在玻璃背板之上，颜色不会被蒙白雾遮暗；
        // 没有外层 clip 约束，按住放大时可以真正溢出导航栏边界
        Box(
            modifier = Modifier
                .offset(x = tabWidth * animatedOffset)
                .width(tabWidth)
                .height(barHeight)
                .padding(indicatorPadding)
                .graphicsLayer {
                    scaleX = indicatorScale
                    scaleY = indicatorScale
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        )

        // 第 3 层：图标 Row——盖在指示块最上面，尺寸和玻璃背板完全一致，手势也挂在这一层
        Row(
            modifier = Modifier
                .width(barWidth)
                .height(barHeight)
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