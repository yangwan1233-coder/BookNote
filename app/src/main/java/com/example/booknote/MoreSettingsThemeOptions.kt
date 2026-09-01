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
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.media3.effect.Crop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.compose.animation.animateContentSize

// 将相册的临时图片复制到 App 的私有目录，实现永久保存
suspend fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
    return withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            // 在应用的私有 files 目录下创建一个名为 bg_image.jpg 的文件
            val file = File(context.filesDir, "bg_image.jpg")
            val outputStream = FileOutputStream(file)

            inputStream.copyTo(outputStream)

            inputStream.close()
            outputStream.close()

            // 返回我们自己私有目录下的绝对路径
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
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
    val context = LocalContext.current // 获取上下文

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { tempUri ->
            scope.launch {
                // 1. 将临时图片复制到永久私有目录
                val localPath = saveImageToInternalStorage(context, tempUri)

                if (localPath != null) {
                    // 2. 更新为图片背景模式
                    themeManager.updateBackgroundType(BackgroundType.IMAGE)
                    // 3. 把存下来的本地绝对路径(localPath)保存到 DataStore/SharedPreferences
                    // 这里假设你有一个 updateBackgroundImage 的方法
                    themeManager.updateBackgroundImage(localPath)
                }
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
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer
                //containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f) // 透光适应全局背景
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
                        //Divider(
                        //   color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        //  thickness = 1.dp,
                        //  modifier = Modifier.padding(bottom = 12.dp)
                        //)

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
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer
                //containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
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
                    var sliderR by remember(themeState.bgColorHex) {
                        mutableFloatStateOf(
                            currentColor.red
                        )
                    }
                    var sliderG by remember(themeState.bgColorHex) {
                        mutableFloatStateOf(
                            currentColor.green
                        )
                    }
                    var sliderB by remember(themeState.bgColorHex) {
                        mutableFloatStateOf(
                            currentColor.blue
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 18.dp)
                    ) {
                        //Divider(
                        //   color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        //  thickness = 1.dp,
                        //  modifier = Modifier.padding(bottom = 12.dp)
                        //)

                        // ================= 第一排：默认 / 图片 =================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        themeManager.updateBackgroundType(
                                            BackgroundType.DEFAULT
                                        )
                                    }
                                },
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
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("图片", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ================= 第二排：自定义预设选色盘 =================
                        Text(
                            text = "快捷纯色方案",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
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
                                val isSelected =
                                    themeState.bgType == BackgroundType.COLOR && themeState.bgColorHex == color.toArgb()
                                        .toLong()
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(
                                                alpha = 0.4f
                                            ),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            scope.launch {
                                                themeManager.updateBackgroundColor(
                                                    color.toArgb().toLong()
                                                )
                                            }
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
                            Text(
                                text = "RGB 自由调色",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
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
                                    color = if (themeState.bgType == BackgroundType.COLOR) MaterialTheme.colorScheme.primary.copy(
                                        alpha = 0.5f
                                    ) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            // R 滑块
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "R",
                                    color = Color(0xFFE53935),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(24.dp)
                                )
                                Slider(
                                    value = sliderR,
                                    onValueChange = { sliderR = it },
                                    // 仅在手指松开时执行数据存储，防止拖动时卡顿
                                    onValueChangeFinished = {
                                        scope.launch {
                                            themeManager.updateBackgroundColor(
                                                Color(
                                                    sliderR,
                                                    sliderG,
                                                    sliderB
                                                ).toArgb().toLong()
                                            )
                                        }
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFE53935),
                                        activeTrackColor = Color(0xFFE53935).copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                )
                            }
                            // G 滑块
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "G",
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(24.dp)
                                )
                                Slider(
                                    value = sliderG,
                                    onValueChange = { sliderG = it },
                                    onValueChangeFinished = {
                                        scope.launch {
                                            themeManager.updateBackgroundColor(
                                                Color(
                                                    sliderR,
                                                    sliderG,
                                                    sliderB
                                                ).toArgb().toLong()
                                            )
                                        }
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF4CAF50),
                                        activeTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                )
                            }
                            // B 滑块
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "B",
                                    color = Color(0xFF1E88E5),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(24.dp)
                                )
                                Slider(
                                    value = sliderB,
                                    onValueChange = { sliderB = it },
                                    onValueChangeFinished = {
                                        scope.launch {
                                            themeManager.updateBackgroundColor(
                                                Color(
                                                    sliderR,
                                                    sliderG,
                                                    sliderB
                                                ).toArgb().toLong()
                                            )
                                        }
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF1E88E5),
                                        activeTrackColor = Color(0xFF1E88E5).copy(alpha = 0.5f)
                                    ),
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
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer
                //containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
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
                        text = "✍️ 笔记字体颜色定制",
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
                    var fontR by remember(themeState.noteTextColorHex) {
                        mutableFloatStateOf(
                            currentFontColor.red
                        )
                    }
                    var fontG by remember(themeState.noteTextColorHex) {
                        mutableFloatStateOf(
                            currentFontColor.green
                        )
                    }
                    var fontB by remember(themeState.noteTextColorHex) {
                        mutableFloatStateOf(
                            currentFontColor.blue
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 18.dp)
                    ) {
                        //Divider(
                        //  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        // thickness = 1.dp,
                        // modifier = Modifier.padding(bottom = 12.dp)
                        //)

                        Text(
                            text = "快捷字体颜色",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
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
                                val isSelected =
                                    themeState.noteTextColorHex == color.toArgb().toLong()
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(
                                                alpha = 0.4f
                                            ),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            scope.launch {
                                                themeManager.updateNoteTextColor(
                                                    color.toArgb().toLong()
                                                )
                                            }
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
                            Text(
                                text = "RGB 字体颜色调色",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "预览文字",
                                    color = Color(fontR, fontG, fontB),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
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
                                Text(
                                    "R",
                                    color = Color(0xFFE53935),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(24.dp)
                                )
                                Slider(
                                    value = fontR,
                                    onValueChange = { fontR = it },
                                    onValueChangeFinished = {
                                        scope.launch {
                                            themeManager.updateNoteTextColor(
                                                Color(
                                                    fontR,
                                                    fontG,
                                                    fontB
                                                ).toArgb().toLong()
                                            )
                                        }
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFE53935),
                                        activeTrackColor = Color(0xFFE53935).copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "G",
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(24.dp)
                                )
                                Slider(
                                    value = fontG,
                                    onValueChange = { fontG = it },
                                    onValueChangeFinished = {
                                        scope.launch {
                                            themeManager.updateNoteTextColor(
                                                Color(
                                                    fontR,
                                                    fontG,
                                                    fontB
                                                ).toArgb().toLong()
                                            )
                                        }
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF4CAF50),
                                        activeTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "B",
                                    color = Color(0xFF1E88E5),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(24.dp)
                                )
                                Slider(
                                    value = fontB,
                                    onValueChange = { fontB = it },
                                    onValueChangeFinished = {
                                        scope.launch {
                                            themeManager.updateNoteTextColor(
                                                Color(
                                                    fontR,
                                                    fontG,
                                                    fontB
                                                ).toArgb().toLong()
                                            )
                                        }
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF1E88E5),
                                        activeTrackColor = Color(0xFF1E88E5).copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                )
                            }
                        }
                    } // 👈 结束内部 Column
                } // 👈 结束 AnimatedVisibility
            } // 👈 结束主 Column
            //Spacer(modifier = Modifier.height(8.dp)) // 放大间距：
        } // 👈 结束字体颜色 Card

        //导图选项
        // ================= 导图选项 =================
        var isExportBgExpanded by remember { mutableStateOf(false) }
        val sharedPref = remember { context.getSharedPreferences("booknote_prefs", Context.MODE_PRIVATE) }
        var selectedPatternName by remember {
            mutableStateOf(sharedPref.getString("export_bg_pattern", "默认纯色") ?: "默认纯色")
        }
        var imageUpdateTrigger by remember { mutableIntStateOf(0) }
        val exportBgArrowRotation by animateFloatAsState(
            targetValue = if (isExportBgExpanded) 180f else 0f,
            label = "ExportBgArrowRotation"
        )


        // 🌟 1. 新增：专属长图导出的相册选择器
        val exportPhotoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri: Uri? ->
            uri?.let { tempUri ->
                scope.launch {
                    val isSuccess = withContext(Dispatchers.IO) {
                        try {
                            val inputStream = context.contentResolver.openInputStream(tempUri)
                            // 单独存一份专属的 export_bg_image.jpg，不干扰全局背景
                            val file = File(context.filesDir, "export_bg_image.jpg")
                            val outputStream = FileOutputStream(file)
                            inputStream?.copyTo(outputStream)
                            inputStream?.close()
                            outputStream.close()
                            true
                        } catch (e: Exception) { false }
                    }
                    if (isSuccess) {
                        selectedPatternName = "自定义图片"
                        sharedPref.edit().putString("export_bg_pattern", "自定义图片").apply()
                    }
                }
            }
        }

        // 🌟 2. 动态读取并【等比缩小】本地图片，用于完美显示缩略图
        var customExportImageBrush by remember {
            mutableStateOf<Brush>(Brush.verticalGradient(listOf(Color(0xFFE0E0E0), Color(0xFF9E9E9E))))
        }

        // 🌟 核心修复 2：监听 selectedPatternName 和 imageUpdateTrigger
        LaunchedEffect(selectedPatternName, imageUpdateTrigger) {
            if (selectedPatternName == "自定义图片") {
                withContext(Dispatchers.IO) {
                    try {
                        val file = File(context.filesDir, "export_bg_image.jpg")
                        if (file.exists()) {
                            // 💡 前提：请确保文件顶部有这个导包：
// import android.graphics.Bitmap

                            val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
                            if (originalBitmap != null) {
                                val targetWidth = 300f
                                val scale = targetWidth / originalBitmap.width
                                val targetHeight = (originalBitmap.height * scale).toInt().coerceAtLeast(1)

                                // 1. 去掉冗长的包名前缀，直接使用 Bitmap
                                val scaledBitmap = Bitmap.createScaledBitmap(
                                    originalBitmap,
                                    targetWidth.toInt(),
                                    targetHeight,
                                    true
                                )

                                // 🌟 2. 核心规范：生成缩略图后，立刻回收庞大的原图，释放内存！
                                if (scaledBitmap != originalBitmap) {
                                    originalBitmap.recycle()
                                }

                                val imageBitmap = scaledBitmap.asImageBitmap()
                                val shader = ImageShader(imageBitmap, TileMode.Clamp, TileMode.Clamp)
                                customExportImageBrush = ShaderBrush(shader)
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }


        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp), // 👈 统一为 28.dp 大圆角
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp), // 👈 增加质感阴影
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer
                //containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 卡片头部（点击可折叠/展开）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExportBgExpanded = !isExportBgExpanded }
                        .padding(horizontal = 20.dp, vertical = 18.dp), // 👈 统一内边距
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.size(24.dp)) // 👈 占位符，保持文字绝对居中

                    Text(
                        text = "🖼️ 分享卡片背景定制", // 👈 加了个 Emoji，保持风格统一
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f) // 👈 占满剩余空间，实现完美居中
                    )

                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "展开或收起",
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(exportBgArrowRotation), // 👈 接入旋转动画
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // 折叠展开的具体内容区域
                AnimatedVisibility(
                    visible = isExportBgExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 🌟 核心修复：加一个 Column 让内容垂直排列
                            Column(
                                modifier = Modifier.padding(10.dp) // 统一在这里加内边距
                            ) {
                                Text(
                                    text = "💡 提示：默认背景上次使用自定义纯色背景。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                //Spacer(modifier = Modifier.height(4.dp)) // 稍微拉开一点距离更好看

                                Text(
                                    text = "💡 当前: $selectedPatternName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "    特色预选图案方案：", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))

                        // 🌟 3. 补齐 6 个选项，自动完美布局
                        val patterns = listOf(
                            "默认纯色" to Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
                            "极光幻彩" to Brush.linearGradient(listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121))),
                            "深空星海" to Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))),
                            "落日余晖" to Brush.horizontalGradient(listOf(Color(0xFFFA709A), Color(0xFFFEE140))),
                            "森系清新" to Brush.verticalGradient(listOf(Color(0xFF134E5E), Color(0xFF71B280))),
                            "自定义图片" to customExportImageBrush // 👈 直接把刚才生成的图片画笔传进来
                        )

                        val rows = patterns.chunked(3)
                        rows.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { (name, brush) ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        PatternThumbnailItem(
                                            name = name,
                                            brush = brush,
                                            isSelected = selectedPatternName == name,
                                            onClick = {
                                                // 🌟 无论选哪个，先把状态切换过去
                                                selectedPatternName = name
                                                sharedPref.edit().putString("export_bg_pattern", name).apply()

                                                if (name == "自定义图片") {
                                                    // 拉起系统相册选择器
                                                    exportPhotoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                                }
                                            }
                                        )
                                    }
                                }
                                repeat(3 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }


            //Spacer(modifier = Modifier.height(8.dp)) // 放大间距：与上方字体卡片拉开更大的呼吸空间
        }


        //导航栏
        //
        // 🌟 1. 新增：玻璃导航栏的折叠状态与箭头旋转动画
        var isLiquidNavExpanded by remember { mutableStateOf(false) }
        val liquidNavArrowRotation by animateFloatAsState(
            targetValue = if (isLiquidNavExpanded) 180f else 0f,
            label = "LiquidNavArrowRotation"
        )

        // 🌟 2. 替换为统一规范的 Card 样式
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(28.dp), // 保持 28.dp 大圆角
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp), // 统一阴影高度
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer
                //containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f) // 统一透光背景色
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 卡片头部（点击可折叠/展开）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isLiquidNavExpanded = !isLiquidNavExpanded }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.size(24.dp)) // 占位符，保持文字居中

                    Text(
                        text = "💎 玻璃导航栏",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f) // 占满剩余空间，实现完美居中
                    )

                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "展开或收起",
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(liquidNavArrowRotation), // 接入旋转动画
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // 折叠展开的具体内容区域
                AnimatedVisibility(
                    visible = isLiquidNavExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 18.dp)
                    ) {
                        // 开关选项包裹在一个带圆角的底色块中，视觉更精美
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "启用液态玻璃效果",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = themeState.isLiquidNavEnabled,
                                onCheckedChange = { isChecked ->
                                    scope.launch { themeManager.updateLiquidNavEnabled(isChecked) }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    }// 👈 这个括号极其重要！它是结束最外层主 Column 的！

    // =================================================================
    // 🎨 调色盘弹窗必须放在主 Column 的外面，保持与 Column 平级
    // =================================================================
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
}// 👈 结束整个 MoreSettingsThemeOptions 函数的最外层大括号
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
                title = {
                    Text(
                        "选择全局背景颜色",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
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

    @Composable
    fun PatternThumbnailItem(
        name: String,
        brush: Brush,
        isSelected: Boolean,
        onClick: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .clickable { onClick() }
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(brush)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(
                            alpha = 0.5f
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
