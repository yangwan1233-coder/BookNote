package com.example.booknote

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.absoluteValue

/**
 * 🌟 顶级丝滑滑动动画修饰符 (全卡片黑洞点状坍缩 - 终极物理飞行版)
 * 让卡片的四个边缘同时向中心坍缩，并将这个中心点猛烈吸入角落极点。
 */
fun Modifier.silkyScrollAnimation(
    listState: LazyListState,
    itemKey: Any
): Modifier = composed {
    val isScrolling = listState.isScrollInProgress

    // 止滑渐隐过渡引擎
    val activeAlphaMultiplier by animateFloatAsState(
        targetValue = if (isScrolling) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
        label = "scrollFade"
    )

    this.graphicsLayer {
        val layoutInfo = listState.layoutInfo
        val itemInfo = layoutInfo.visibleItemsInfo.find { it.key == itemKey }

        if (itemInfo != null) {
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val itemCenter = itemInfo.offset + itemInfo.size / 2
            val viewportCenter = viewportHeight / 2

            val fraction = ((itemCenter - viewportCenter).toFloat() / viewportCenter.toFloat()).coerceIn(-1f, 1f)

            // =========================================================
            // 🛡️ 1. 触发区设定
            // =========================================================
            val (safeZone, endZone) = if (fraction < 0) {
                0.96f to 1f // 上半屏状态栏黑洞
            } else {
                0.56f to 0.6f // 下半屏导航栏黑洞
            }

            if (fraction.absoluteValue <= safeZone) {
                scaleX = 1f
                scaleY = 1f
                alpha = 1f
                rotationZ = 0f
                translationX = 0f
                translationY = 0f
                return@graphicsLayer
            }

            val linearProgress = ((fraction.absoluteValue - safeZone) / (endZone - safeZone)).coerceIn(0f, 1f)

            // 物理三次方缓动，加速吸入感
            val edgeProgress = linearProgress
            // =========================================================
            // 🎯 2. 🌟 核心破局点：中心坍缩锚点
            // =========================================================
            // 不再钉死左边缘，而是设定在卡片的【正中心】(0.5f, 0.5f)
            // 这样缩小的时候，左右两端、上下两端都会同步向中间剧烈收缩！
            transformOrigin = TransformOrigin(0.5f, 0.5f)
            rotationZ = 0f

            // =========================================================
            // 📐 3. 四向同步极致坍缩
            // =========================================================
            val currentScale = (1f - edgeProgress).coerceAtLeast(0f)
            scaleX = currentScale
            scaleY = currentScale

            val baseAlpha = (1f - edgeProgress).coerceAtLeast(0f)
            alpha = if (linearProgress > 0.05f) {
                baseAlpha * activeAlphaMultiplier
            } else {
                baseAlpha
            }

            // =========================================================
            // 🚀 4. 🌟 真正的“吸入”物理轨迹算法
            // =========================================================
            // 因为锚点在中心，我们需要把这个“正在缩小的中心点”强行拽到屏幕左侧！
            // size.width 是当前卡片的实际宽度。向左偏移宽度的一半，刚好砸进左边缘！
            translationX = -(size.width / 2f) * edgeProgress

            // 同理，向上下拽入角落。加入一点额外推力(60f)确保彻底进入状态栏/导航栏的深渊
            translationY = if (fraction < 0) {
                -(size.height / 2f + 60f) * edgeProgress
            } else {
                (size.height / 2f + 60f) * edgeProgress
            }
        }
    }
}