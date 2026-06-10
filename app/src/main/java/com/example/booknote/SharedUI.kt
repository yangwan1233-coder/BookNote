package com.example.booknote

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageGrid(paths: List<String>, columns: Int = 3, modifier: Modifier = Modifier, onImageClick: (String) -> Unit = {}, onImageLongClick: (String) -> Unit = {}) {
    if (paths.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        paths.chunked(columns).forEach { rowPaths ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowPaths.forEach { path ->
                    AsyncImage(
                        model = Uri.parse(path), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                            .combinedClickable(onClick = { onImageClick(path) }, onLongClick = { onImageLongClick(path) })
                    )
                }
                repeat(columns - rowPaths.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun FloatingBottomBar(navController: NavHostController, currentRoute: String?, modifier: Modifier = Modifier) {
    val selectedIndex = when (currentRoute) { "home" -> 0; "settings" -> 1; else -> 2 }
    val bubbleOffset by animateDpAsState(targetValue = (selectedIndex * 64).dp, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow), label = "bubble")
    Box(modifier = modifier.fillMaxWidth().padding(bottom = 40.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 8.dp, shadowElevation = 8.dp) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
                Box(modifier = Modifier.offset(x = bubbleOffset).size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navController.navigate("home") { launchSingleTop = true } }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Home, null, tint = if (selectedIndex == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { navController.navigate("settings") { launchSingleTop = true } }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Settings, null, tint = if (selectedIndex == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { navController.navigate("edit/new") }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Add, null, modifier = Modifier.scale(1.4f), tint = if (selectedIndex == 2) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
@Composable
fun YearHeader(year: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        // 【核心修改点】增加了 shadowElevation = 8.dp，并去掉了半透明效果以保证阴影纯净
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 8.dp
        ) {
            Text(
                text = year,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }
}
// 核心优化：纯手工打造的左滑悬停组件（加入立体阴影与顶级物理弹簧动效）
@Composable
fun SwipeHoverNoteCard(note: Note, showDate: Boolean, onClick: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current

    // 【修改点 1】向左再移动一点：将悬停漏出的最大宽度从 120.dp 扩大到 160.dp，空间更宽裕防误触
    val buttonWidth = 160.dp
    val buttonWidthPx = with(density) { buttonWidth.toPx() }

    // 用于操作按钮的点击“触觉缩放”动画状态
    var archiveScale by remember { mutableStateOf(1f) }
    var deleteScale by remember { mutableStateOf(1f) }
    val animatedArchiveScale by androidx.compose.animation.core.animateFloatAsState(targetValue = archiveScale, label = "archive")
    val animatedDeleteScale by androidx.compose.animation.core.animateFloatAsState(targetValue = deleteScale, label = "delete")

    Box(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // 底层：露出存档和删除按钮
        Row(
            modifier = Modifier.fillMaxSize().padding(end = 24.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 【修改点 2】操作时的顶级动画：点击时按钮先回缩，随后卡片自动平滑归位，最后执行归档
            IconButton(
                onClick = {
                    scope.launch {
                        archiveScale = 0.8f // 按下回缩反馈
                        kotlinx.coroutines.delay(100)
                        archiveScale = 1f   // 迅速弹回
                        // 挂起等待：先让卡片丝滑地滑回 0 的位置遮住按钮
                        offsetX.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                        onArchive() // 动画播放完毕后，真正执行底层归档操作
                    }
                },
                modifier = Modifier.size(48.dp).scale(animatedArchiveScale).background(Color(0xFF4CAF50), CircleShape)
            ) { Icon(Icons.Default.Archive, "存档", tint = Color.White) }

            Spacer(modifier = Modifier.width(16.dp))

            IconButton(
                onClick = {
                    scope.launch {
                        deleteScale = 0.8f // 按下回缩反馈
                        kotlinx.coroutines.delay(100)
                        deleteScale = 1f   // 迅速弹回
                        // 挂起等待：先让卡片丝滑地滑回 0 的位置遮住按钮
                        offsetX.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                        onDelete() // 动画播放完毕后，真正执行底层删除操作
                    }
                },
                modifier = Modifier.size(48.dp).scale(animatedDeleteScale).background(Color(0xFFF44336), CircleShape)
            ) { Icon(Icons.Default.Delete, "删除", tint = Color.White) }
        }

        // 顶层：笔记卡片，带滑动手势与阴影
        Card(
            modifier = Modifier.fillMaxWidth().offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                // 【修改点 3】悬停和归位动画：加入阻尼物理弹簧 (spring) 效果，让回弹和吸附充满 Q 弹质感
                                if (offsetX.value < -buttonWidthPx / 2) {
                                    offsetX.animateTo(-buttonWidthPx, spring(dampingRatio = 0.65f, stiffness = 300f))
                                } else {
                                    offsetX.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 300f))
                                }
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            val newOffset = (offsetX.value + dragAmount).coerceIn(-buttonWidthPx, 0f)
                            offsetX.snapTo(newOffset)
                        }
                    }
                }.clickable(onClick = onClick),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            // 【修改点 4】增加 8.dp 阴影：使其拥有与悬浮底部导航栏完全一致的立体层次感！
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(vertical = 18.dp, horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showDate) Text(text = formatTime(note.createdAt), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), modifier = Modifier.padding(end = 12.dp))
                if (note.imagePaths.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f).padding(end = 12.dp)) { AsyncImage(model = Uri.parse(note.imagePaths.first()), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))) }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(text = if (note.title.isNotBlank()) note.title else "无标题", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = if (note.content.isNotBlank()) note.content else "空内容", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}