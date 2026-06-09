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
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
            Text(text = year, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }
    }
}

// 核心优化：纯手工打造的左滑悬停组件（完美解决原生组件无法悬停露出的痛点）
@Composable
fun SwipeHoverNoteCard(note: Note, showDate: Boolean, onClick: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val buttonWidth = 120.dp
    val buttonWidthPx = with(density) { buttonWidth.toPx() }

    Box(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // 底层：露出存档和删除按钮
        Row(modifier = Modifier.fillMaxSize().padding(end = 16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { scope.launch { offsetX.animateTo(0f) }; onArchive() }, modifier = Modifier.size(48.dp).background(Color(0xFF4CAF50), CircleShape)) { Icon(Icons.Default.Archive, "存档", tint = Color.White) }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { scope.launch { offsetX.animateTo(0f) }; onDelete() }, modifier = Modifier.size(48.dp).background(Color(0xFFF44336), CircleShape)) { Icon(Icons.Default.Delete, "删除", tint = Color.White) }
        }

        // 顶层：笔记卡片，带滑动手势
        Card(
            modifier = Modifier.fillMaxWidth().offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                // 松手时，如果滑动超过一半按钮宽度，就悬停展开；否则回弹关闭
                                if (offsetX.value < -buttonWidthPx / 2) offsetX.animateTo(-buttonWidthPx, spring())
                                else offsetX.animateTo(0f, spring())
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
            shape = CircleShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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