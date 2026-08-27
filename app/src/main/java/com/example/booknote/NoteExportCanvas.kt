package com.example.booknote

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shreyaspatil.capturable.Capturable
import dev.shreyaspatil.capturable.controller.CaptureController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NoteExportCanvas(
    isExporting: Boolean,
    captureController: CaptureController,
    title: String,
    timestamp: Long,
    imagePaths: List<String>,
    activeBlocks: List<UIBlock>,
    themeState: AppThemeState,
    customTextColor: Color,
    exportBackgroundBrush: Brush? = null, // 🌟 新增：专属长图背景接口（支持自定义图案/渐变/纯色）
    onCaptureComplete: (Bitmap?) -> Unit
) {
    if (isExporting) {
        val canvasWidth = 420.dp

        Box(
            modifier = Modifier
                .size(0.dp)
                .graphicsLayer { alpha = 0.01f }
        ) {
            Box(modifier = Modifier.wrapContentSize(unbounded = true)) {
                Capturable(
                    controller = captureController,
                    onCaptured = { imageBitmap, _ ->
                        onCaptureComplete(imageBitmap?.asAndroidBitmap())
                    }
                ) {
                    // ==========================================
                    // 🎨 整体长图画布根背景：优先采用专属背景接口，没有则用纯色
                    // ==========================================
                    val defaultBrush = Brush.verticalGradient(
                        colors = listOf(Color(themeState.bgColorHex), Color(themeState.bgColorHex))
                    )

                    // ==========================================
                    // 🎨 外层容器：底层隐形层，设为完全透明！
                    // 仅利用 padding 撑开安全区，确保圆角和阴影不会被切掉
                    // ==========================================
                    Box(
                        modifier = Modifier
                            .width(canvasWidth)
                            .wrapContentHeight(unbounded = true)
                            .background(Color.Transparent) // 🌟 恢复为透明隐形底层，绝不修改
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        // ==========================================
                        // 💳 内层卡片本体：上层背景、恢复大圆角、弥散阴影
                        // ==========================================
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                // 🌟 1. 先铺底色：保证“默认纯色”预设时有主题色兜底，且恢复 32dp 圆角
                                .background(
                                    color = Color(themeState.bgColorHex),
                                    shape = RoundedCornerShape(32.dp)
                                )
                                // 🌟 2. 叠加导图预设背景：如果有特色图案，就完美覆盖在底色上
                                .then(
                                    if (exportBackgroundBrush != null) {
                                        Modifier.background(
                                            brush = exportBackgroundBrush,
                                            shape = RoundedCornerShape(32.dp) // 同步加上圆角
                                        )
                                    } else Modifier
                                )
                                // 🌟 3. 拟态风高光描边
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.8f),
                                            Color.White.copy(alpha = 0.1f),
                                            Color.White.copy(alpha = 0.3f)
                                        ),
                                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                        end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                                    ),
                                    shape = RoundedCornerShape(32.dp)
                                ),
                            shape = RoundedCornerShape(32.dp),
                            color = Color.Transparent, // 🌟 表面必须透明，才能透出上面 Modifier 里画好的背景
                            shadowElevation = 16.dp // 柔和弥散阴影
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // ------------------------------------
                                // ① 顶部：居中带阴影的品牌胶囊
                                // (👇 下面的代码一字不差，继续保留你的内容)
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shadowElevation = 8.dp,
                                    modifier = Modifier.padding(bottom = 32.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Book,
                                            contentDescription = "App Icon",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "BookNote",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }

                                // ② 标题
                                Text(
                                    text = if (title.isBlank()) "无标题笔记" else title,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = customTextColor,
                                    lineHeight = 38.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(28.dp))

                                // ③ 内容区
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    if (imagePaths.isNotEmpty()) {
                                        ImageGrid(
                                            paths = imagePaths,
                                            columns = 3,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 24.dp)
                                                .clip(RoundedCornerShape(16.dp)),
                                            onImageClick = {},
                                            onImageLongClick = {}
                                        )
                                    }

                                    activeBlocks.forEach { block ->
                                        when (block) {
                                            is UITextBlock -> {
                                                if (block.content.text.isNotBlank()) {
                                                    Text(
                                                        text = block.content.text,
                                                        fontSize = 20.sp,
                                                        color = customTextColor.copy(alpha = 0.85f),
                                                        lineHeight = 28.sp,
                                                        letterSpacing = 0.5.sp,
                                                        modifier = Modifier.padding(vertical = 6.dp)
                                                    )
                                                }
                                            }
                                            is UITableBlock -> {
                                                Box(modifier = Modifier.padding(vertical = 12.dp)) {
                                                    InteractiveTableBlock(tableData = block.tableData, onUpdate = {}, onDelete = {})
                                                }
                                            }
                                            is UIMindMapBlock -> {
                                                Box(modifier = Modifier.padding(vertical = 12.dp)) {
                                                    InteractiveMindMapBlock(
                                                        title = block.title, rootNode = block.rootNode, selectedNodeId = null,
                                                        onTitleChange = {}, onNodeSelect = {}, onUpdate = {}, onDeleteMap = {}
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                                HorizontalDivider(
                                    thickness = 2.dp,
                                    color = customTextColor.copy(alpha = 0.51f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
                                Text(
                                    text = "来自 BookNote · $dateStr",
                                    color = customTextColor.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}