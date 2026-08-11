package com.example.booknote // 👈 请保持您原有的 package 路径

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun MoreSettingsThemeOptions(
    themeState: AppThemeState,
    themeManager: ThemeSettingsManager
) {
    val scope = rememberCoroutineScope()
    var showColorPickerDialog by remember { mutableStateOf(false) }

    // 折叠状态控制（默认收起）
    var isThemeModeExpanded by remember { mutableStateOf(false) }
    var isCustomBgExpanded by remember { mutableStateOf(false) }

    // 旋转箭头动画
    val themeArrowRotation by animateFloatAsState(
        targetValue = if (isThemeModeExpanded) 180f else 0f,
        label = "ThemeArrowRotation"
    )
    val bgArrowRotation by animateFloatAsState(
        targetValue = if (isCustomBgExpanded) 180f else 0f,
        label = "BgArrowRotation"
    )

    // 相册选择器
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                themeManager.updateBackgroundImage(it.toString())
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =================================================================
        // 🌟 卡片一：外观主题模式（28.dp大圆角 + 悬浮阴影 + 折叠动画 + 居中标题）
        // =================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp), // 👈 增大圆角，与底部导航栏一致
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp), // 👈 增加质感阴影
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f) // 透光适应全局背景
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 可点击的头部标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isThemeModeExpanded = !isThemeModeExpanded }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.size(24.dp)) // 占位保持文字绝对居中

                    Text(
                        text = "🌓 外观主题模式",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f) // 👈 标题完美居中
                    )

                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "展开或收起",
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(themeArrowRotation), // 👈 顺滑旋转动画
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // 折叠展开内容区域
                AnimatedVisibility(
                    visible = isThemeModeExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 18.dp)
                    ) {
                        Divider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // 第一排：跟随系统 / 浅色模式 / 深色模式
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val modes = listOf(
                                Triple("跟随系统", ThemeMode.SYSTEM, "📱"),
                                Triple("浅色模式", ThemeMode.LIGHT, "☀️"),
                                Triple("深色模式", ThemeMode.DARK, "🌙")
                            )

                            modes.forEach { (label, mode, icon) ->
                                val isSelected = themeState.themeMode == mode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        scope.launch { themeManager.updateThemeMode(mode) }
                                    },
                                    label = {
                                        Text(
                                            "$icon $label",
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 🌟 新增第二排：手动按钮 - 状态栏反色
                        val isInverted = themeState.isStatusBarIconInverted
                        FilterChip(
                            selected = isInverted,
                            onClick = {
                                scope.launch {
                                    themeManager.updateStatusBarIconInverted(!isInverted)
                                }
                            },
                            label = {
                                Text(
                                    text = if (isInverted) "✨ 状态栏反色 (已开启)" else "🌗 状态栏反色 (已关闭)",
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // =================================================================
        // 🌟 卡片二：全局自定义背景（28.dp大圆角 + 悬浮阴影 + 折叠动画 + 居中标题）
        // =================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp), // 👈 增大圆角
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp), // 👈 增加阴影
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 可点击的头部标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCustomBgExpanded = !isCustomBgExpanded }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.size(24.dp)) // 占位保持居中

                    Text(
                        text = "🎨 全局自定义背景",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f) // 👈 标题完美居中
                    )

                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "展开或收起",
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(bgArrowRotation), // 👈 顺滑旋转动画
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // 折叠展开内容区域
                AnimatedVisibility(
                    visible = isCustomBgExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    // 读取当前颜色以初始化 RGB 滑块状态 (范围 0f - 1f)
                    val currentColor = Color(themeState.bgColorHex)
                    var sliderR by remember(themeState.bgColorHex) { mutableFloatStateOf(currentColor.red) }
                    var sliderG by remember(themeState.bgColorHex) { mutableFloatStateOf(currentColor.green) }
                    var sliderB by remember(themeState.bgColorHex) { mutableFloatStateOf(currentColor.blue) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 18.dp)
                    ) {
                        Divider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // ================= 第一排：默认 / 图片 =================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { scope.launch { themeManager.updateBackgroundType(BackgroundType.DEFAULT) } },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = if (themeState.bgType == BackgroundType.DEFAULT)
                                    ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text("默认", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = if (themeState.bgType == BackgroundType.IMAGE)
                                    ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("图片", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ================= 第二排：自定义预设选色盘 =================
                        Text(text = "快捷纯色方案", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(8.dp))

                        val presetColors = listOf(
                            Color(0xFFF5F5F5), Color(0xFFE3F2FD), Color(0xFFE8F5E9),
                            Color(0xFFFFF3E0), Color(0xFFF3E5F5), Color(0xFFFCE4EC),
                            Color(0xFF212121), Color(0xFF121212), Color(0xFF1E1E2C)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            presetColors.forEach { color ->
                                val isSelected = themeState.bgType == BackgroundType.COLOR && themeState.bgColorHex == color.toArgb().toLong()
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            scope.launch { themeManager.updateBackgroundColor(color.toArgb().toLong()) }
                                        }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ================= 第三排：RGB 自由选色滑轮 =================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "RGB 自由调色", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            // 实时颜色预览小圆点
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(sliderR, sliderG, sliderB))
                                    .border(1.dp, Color.Gray, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .border(
                                    width = 1.dp,
                                    color = if (themeState.bgType == BackgroundType.COLOR) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            // R 滑块
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("R", color = Color(0xFFE53935), fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                                Slider(
                                    value = sliderR,
                                    onValueChange = { sliderR = it },
                                    // 仅在手指松开时执行数据存储，防止拖动时卡顿
                                    onValueChangeFinished = {
                                        scope.launch { themeManager.updateBackgroundColor(Color(sliderR, sliderG, sliderB).toArgb().toLong()) }
                                    },
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFE53935), activeTrackColor = Color(0xFFE53935).copy(alpha = 0.5f)),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                )
                            }
                            // G 滑块
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("G", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                                Slider(
                                    value = sliderG,
                                    onValueChange = { sliderG = it },
                                    onValueChangeFinished = {
                                        scope.launch { themeManager.updateBackgroundColor(Color(sliderR, sliderG, sliderB).toArgb().toLong()) }
                                    },
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF4CAF50), activeTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                )
                            }
                            // B 滑块
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("B", color = Color(0xFF1E88E5), fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                                Slider(
                                    value = sliderB,
                                    onValueChange = { sliderB = it },
                                    onValueChangeFinished = {
                                        scope.launch { themeManager.updateBackgroundColor(Color(sliderR, sliderG, sliderB).toArgb().toLong()) }
                                    },
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF1E88E5), activeTrackColor = Color(0xFF1E88E5).copy(alpha = 0.5f)),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        // =================================================================
// 🌟 新加卡片：自定义笔记字体颜色（完全独立 Card）
// =================================================================
        var isNoteFontColorExpanded by remember { mutableStateOf(false) }
        val noteFontArrowRotation by animateFloatAsState(
            targetValue = if (isNoteFontColorExpanded) 180f else 0f,
            label = "NoteFontArrowRotation"
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isNoteFontColorExpanded = !isNoteFontColorExpanded }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.size(24.dp))

                    Text(
                        text = "✍️ 自定义笔记字体颜色",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "展开或收起",
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(noteFontArrowRotation),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                AnimatedVisibility(
                    visible = isNoteFontColorExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val currentFontColor = Color(themeState.noteTextColorHex)
                    var fontR by remember(themeState.noteTextColorHex) { mutableFloatStateOf(currentFontColor.red) }
                    var fontG by remember(themeState.noteTextColorHex) { mutableFloatStateOf(currentFontColor.green) }
                    var fontB by remember(themeState.noteTextColorHex) { mutableFloatStateOf(currentFontColor.blue) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 18.dp)
                    ) {
                        Divider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Text(text = "快捷字体颜色", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(8.dp))

                        val presetFontColors = listOf(
                            Color(0xFF000000), Color(0xFF212121), Color(0xFF757575),
                            Color(0xFFD32F2F), Color(0xFF1976D2), Color(0xFF388E3C),
                            Color(0xFFF57C00), Color(0xFF7B1FA2), Color(0xFFFFFFFF)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            presetFontColors.forEach { color ->
                                val isSelected = themeState.noteTextColorHex == color.toArgb().toLong()
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            scope.launch { themeManager.updateNoteTextColor(color.toArgb().toLong()) }
                                        }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "RGB 字体颜色调色", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("预览文字", color = Color(fontR, fontG, fontB), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(fontR, fontG, fontB))
                                        .border(1.dp, Color.Gray, CircleShape)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("R", color = Color(0xFFE53935), fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                                Slider(
                                    value = fontR,
                                    onValueChange = { fontR = it },
                                    onValueChangeFinished = {
                                        scope.launch { themeManager.updateNoteTextColor(Color(fontR, fontG, fontB).toArgb().toLong()) }
                                    },
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFE53935), activeTrackColor = Color(0xFFE53935).copy(alpha = 0.5f)),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("G", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                                Slider(
                                    value = fontG,
                                    onValueChange = { fontG = it },
                                    onValueChangeFinished = {
                                        scope.launch { themeManager.updateNoteTextColor(Color(fontR, fontG, fontB).toArgb().toLong()) }
                                    },
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF4CAF50), activeTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("B", color = Color(0xFF1E88E5), fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                                Slider(
                                    value = fontB,
                                    onValueChange = { fontB = it },
                                    onValueChangeFinished = {
                                        scope.launch { themeManager.updateNoteTextColor(Color(fontR, fontG, fontB).toArgb().toLong()) }
                                    },
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF1E88E5), activeTrackColor = Color(0xFF1E88E5).copy(alpha = 0.5f)),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 调色盘选色弹窗（原逻辑保留）
    if (showColorPickerDialog) {
        ColorWheelPickerDialog(
            currentColor = Color(themeState.bgColorHex),
            onDismiss = { showColorPickerDialog = false },
            onColorSelected = { selectedColor ->
                scope.launch {
                    themeManager.updateBackgroundColor(selectedColor.toArgb().toLong())
                }
                showColorPickerDialog = false
            }
        )
    }
}

// 快速调色盘弹窗组件
@Composable
fun ColorWheelPickerDialog(
    currentColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    val presetColors = listOf(
        Color(0xFFF5F5F5), Color(0xFFE3F2FD), Color(0xFFE8F5E9),
        Color(0xFFFFF3E0), Color(0xFFF3E5F5), Color(0xFFFCE4EC),
        Color(0xFF212121), Color(0xFF121212), Color(0xFF1E1E2C)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择全局背景颜色", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("预设色盘：", fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    presetColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(1.dp, Color.Gray, CircleShape)
                                .clickable { onColorSelected(color) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}