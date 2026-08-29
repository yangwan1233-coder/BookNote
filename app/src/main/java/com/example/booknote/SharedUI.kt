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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    // 【核心修改】：精准去除了死板的 40.dp，替换为大厂级全面屏动态避让逻辑
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
            ),
        contentAlignment = Alignment.Center
    ) {
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
    // 💡 【核心修复】：删除了外层的 padding(vertical = 16.dp)
    // 把外部间距的控制权，100% 交还给外层的 LazyColumn 和 items 探测逻辑！
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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
                // ✅ 这个内部 padding 是维持椭圆气泡形状的，完美保留
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
// 核心优化：修改归档动画，使卡片与按钮作为一个整体向右飞出
// 🌟 1. 修改配方，接收父级传来的状态和回调
@Composable
fun SwipeHoverNoteCard(
    note: Note,
    showDate: Boolean,
    currentlySwipedId: String?, // 👈 新增：当前被滑开的卡片 ID
    onSwipeStateChange: (String?) -> Unit, // 👈 新增：通知父级更新状态
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val archiveOffsetX = remember { Animatable(0f) }
    val density = LocalDensity.current

    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    val buttonWidth = 160.dp
    val buttonWidthPx = with(density) { buttonWidth.toPx() }

    var archiveScale by remember { mutableStateOf(1f) }
    var deleteScale by remember { mutableStateOf(1f) }
    val animatedArchiveScale by androidx.compose.animation.core.animateFloatAsState(targetValue = archiveScale, label = "archive")
    val animatedDeleteScale by androidx.compose.animation.core.animateFloatAsState(targetValue = deleteScale, label = "delete")

    // 🌟 2. 监听父级状态：如果被滑开的不是自己，自己就乖乖缩回去
    LaunchedEffect(currentlySwipedId) {
        if (currentlySwipedId != note.id && offsetX.targetValue < 0f) {
            offsetX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 300f))
        }
    }

    // 最外层容器
    Box(modifier = Modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Min)
        .offset { IntOffset(archiveOffsetX.value.roundToInt(), 0) }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(end = 24.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 🌟 1. 计算当前卡片滑动的进度 (0f 到 1f)
            // 当 offsetX 从 0 滑到 -buttonWidthPx 时，进度从 0 变成 1
            val revealProgress = (-offsetX.value / buttonWidthPx).coerceIn(0f, 1f)

            // 🌟 2. 算好联动参数
            val dynamicScale = 0.6f + 0.4f * revealProgress // 按钮大小从 0.6 放大到 1.0
            val dynamicAlpha = revealProgress // 透明度从 0 到 1
            val parallaxX = 40f * (1f - revealProgress) // 视差：刚开始向右偏移 40 像素，慢慢归位

            // 归档按钮
            IconButton(
                onClick = {
                    scope.launch {
                        archiveScale = 0.8f
                        kotlinx.coroutines.delay(80)
                        archiveScale = 1f
                        archiveOffsetX.animateTo(
                            targetValue = screenWidthPx * 1.2f,
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 250,
                                easing = androidx.compose.animation.core.FastOutLinearInEasing
                            )
                        )
                        onArchive()
                        onSwipeStateChange(null)
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    // 🌟 【绝杀修改】：用 graphicsLayer 将跟手联动和点击动画完美结合
                    .graphicsLayer {
                        scaleX = dynamicScale * animatedArchiveScale
                        scaleY = dynamicScale * animatedArchiveScale
                        alpha = dynamicAlpha
                        translationX = parallaxX
                    }
                    //.shadow(elevation = 8.dp, shape = CircleShape)
                    .background(Color(0xFF4CAF50), CircleShape)
            ) {
                Icon(Icons.Default.Archive, "存档", tint = Color.White)
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 删除按钮
            IconButton(
                onClick = {
                    scope.launch {
                        deleteScale = 0.8f
                        kotlinx.coroutines.delay(80)
                        deleteScale = 1f
                        archiveOffsetX.animateTo(
                            targetValue = -screenWidthPx * 1.2f,
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 250,
                                easing = androidx.compose.animation.core.FastOutLinearInEasing
                            )
                        )
                        onDelete()
                        onSwipeStateChange(null)
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    // 🌟 【绝杀修改】：同样注入联动引擎，为了让两个按钮有层次感，删除按钮的视差可以更大一点点
                    .graphicsLayer {
                        scaleX = dynamicScale * animatedDeleteScale
                        scaleY = dynamicScale * animatedDeleteScale
                        alpha = dynamicAlpha
                        translationX = parallaxX * 1.5f // 👈 删除按钮在最右侧，让它位移稍大一点，错落有致
                    }
                    //.shadow(elevation = 8.dp, shape = CircleShape)
                    .background(Color(0xFFF44336), CircleShape)
            ) {
                Icon(Icons.Default.Delete, "删除", tint = Color.White)
            }
        }

        // 顶层：笔记卡片
        Card(
            modifier = Modifier.fillMaxWidth().offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            onSwipeStateChange(note.id) // 🌟 刚开始拖拽，就告诉父级“我被滑开了”
                        },
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value < -buttonWidthPx / 2) {
                                    // 🌟 3. 滑到尽头时，阻尼降低，产生 Q 弹动效
                                    offsetX.animateTo(-buttonWidthPx, spring(dampingRatio = 0.45f, stiffness = 350f))
                                } else {
                                    // 没滑到一半松手，干脆收回
                                    offsetX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 300f))
                                    if (currentlySwipedId == note.id) onSwipeStateChange(null)
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
                }
                .clickable {
                    // 🌟 4. 如果处于滑开状态，点击本体只是收回，不会进入编辑页
                    if (offsetX.targetValue < 0f) {
                        scope.launch {
                            offsetX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 300f))
                            if (currentlySwipedId == note.id) onSwipeStateChange(null)
                        }
                    } else {
                        onClick()
                    }
                },
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showDate) Text(text = formatTime(note.createdAt), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), modifier = Modifier.padding(end = 12.dp))

                if (note.imagePaths.isNotEmpty()) {
                    NoteImageGrid(imagePaths = note.imagePaths, modifier = Modifier.padding(end = 12.dp))
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
// ================== 独立的九宫格渲染组件 ==================
@Composable
fun NoteImageGrid(imagePaths: List<String>, modifier: Modifier = Modifier) {
    val images = imagePaths.take(9)
    val count = images.size
    val spacing = 2.dp

    // 最外层容器接收传入的 modifier (如 padding)
    Box(modifier = modifier) {
        // 内层容器固定 55.dp (完美遵循您的设定)
        Box(
            modifier = Modifier
                .size(55.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            when (count) {
                1 -> {
                    AsyncImage(model = Uri.parse(images[0]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                2 -> {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                        AsyncImage(model = Uri.parse(images[0]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        AsyncImage(model = Uri.parse(images[1]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
                3 -> {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                        AsyncImage(model = Uri.parse(images[0]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                            AsyncImage(model = Uri.parse(images[1]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                            AsyncImage(model = Uri.parse(images[2]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
                4 -> {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            AsyncImage(model = Uri.parse(images[0]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            AsyncImage(model = Uri.parse(images[1]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            AsyncImage(model = Uri.parse(images[2]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            AsyncImage(model = Uri.parse(images[3]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
                5 -> {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                        Row(modifier = Modifier.weight(2f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            AsyncImage(model = Uri.parse(images[0]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(2f).fillMaxHeight())
                            AsyncImage(model = Uri.parse(images[1]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            AsyncImage(model = Uri.parse(images[2]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            AsyncImage(model = Uri.parse(images[3]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            AsyncImage(model = Uri.parse(images[4]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
                6 -> {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                        Row(modifier = Modifier.weight(2f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            AsyncImage(model = Uri.parse(images[0]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(2f).fillMaxHeight())
                            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                                AsyncImage(model = Uri.parse(images[1]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                                AsyncImage(model = Uri.parse(images[2]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                            }
                        }
                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            AsyncImage(model = Uri.parse(images[3]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            AsyncImage(model = Uri.parse(images[4]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            AsyncImage(model = Uri.parse(images[5]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
                7 -> {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                        Row(modifier = Modifier.weight(2f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            AsyncImage(model = Uri.parse(images[0]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            AsyncImage(model = Uri.parse(images[1]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                                AsyncImage(model = Uri.parse(images[2]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                                AsyncImage(model = Uri.parse(images[3]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                            }
                        }
                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            AsyncImage(model = Uri.parse(images[4]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            AsyncImage(model = Uri.parse(images[5]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            AsyncImage(model = Uri.parse(images[6]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
                8 -> {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                        Row(modifier = Modifier.weight(2f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            AsyncImage(model = Uri.parse(images[0]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                                AsyncImage(model = Uri.parse(images[1]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                                AsyncImage(model = Uri.parse(images[2]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                            }
                            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                                AsyncImage(model = Uri.parse(images[3]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                                AsyncImage(model = Uri.parse(images[4]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                            }
                        }
                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            AsyncImage(model = Uri.parse(images[5]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            AsyncImage(model = Uri.parse(images[6]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            AsyncImage(model = Uri.parse(images[7]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            AsyncImage(model = Uri.parse(images[0]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            AsyncImage(model = Uri.parse(images[1]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            AsyncImage(model = Uri.parse(images[2]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            AsyncImage(model = Uri.parse(images[3]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            AsyncImage(model = Uri.parse(images[4]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            AsyncImage(model = Uri.parse(images[5]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            AsyncImage(model = Uri.parse(images[6]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            AsyncImage(model = Uri.parse(images[7]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            AsyncImage(model = Uri.parse(images[8]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}