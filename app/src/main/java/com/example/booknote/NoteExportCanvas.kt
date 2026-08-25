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
    onCaptureComplete: (Bitmap?) -> Unit
) {
    if (isExporting) {
        // 🌟 锁死长图宽度，保证文字正确换行
        val canvasWidth = 420.dp

        Box(
            modifier = Modifier
                .size(0.dp) // 隐藏渲染舱，不干扰当前屏幕
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
                    // 🎨 外层容器：去掉 9:16，背景设为完全透明（隐形）
                    // 仅利用 padding 撑开安全区，确保圆角和阴影不会被切掉
                    // ==========================================
                    Box(
                        modifier = Modifier
                            .width(canvasWidth)
                            .wrapContentHeight(unbounded = true)
                            .background(Color.Transparent) // 🌟 隐形外层背景
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        // ==========================================
                        // 💳 内层卡片本体：自带超大圆角、弥散阴影与高光描边
                        // ==========================================
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                // 🌟 新增：给内层卡片添加拟态风高光描边
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
                                    shape = RoundedCornerShape(32.dp) // 与表面圆角保持一致
                                ),
                            shape = RoundedCornerShape(32.dp),
                            color = Color(themeState.bgColorHex), // 取自用户主题的底色
                            shadowElevation = 16.dp // 柔和弥散阴影
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally // 🌟 全局横向居中基础设定
                            ) {
                                // ------------------------------------
                                // ① 顶部：居中带阴影的品牌胶囊
                                // ------------------------------------
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant, // 柔和的胶囊底色
                                    shadowElevation = 8.dp, // 🌟 胶囊弥散阴影
                                    modifier = Modifier.padding(bottom = 32.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Book, // 软件图标
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

                                // ------------------------------------
                                // ② 笔记大标题 (完全居中)
                                // ------------------------------------
                                Text(
                                    text = if (title.isBlank()) "无标题笔记" else title,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = customTextColor,
                                    lineHeight = 38.sp,
                                    textAlign = TextAlign.Center, // 🌟 文本居中
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(28.dp))

                                // ==========================================
                                // 🌟 内容区容器 (恢复左对齐排版)
                                // ==========================================
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.Start // 内部图文恢复正常的左对齐
                                ) {
                                    // ③ 图片展区
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

                                    // ④ 富文本正文与模块
                                    activeBlocks.forEach { block ->
                                        when (block) {
                                            is UITextBlock -> {
                                                if (block.content.text.isNotBlank()) {
                                                    Text(
                                                        text = block.content.text,
                                                        fontSize = 15.sp,
                                                        color = customTextColor.copy(alpha = 0.96f),
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

                                // ------------------------------------
                                // ⑤ 底部：加粗分割线并缩小间距
                                // ------------------------------------
                                Spacer(modifier = Modifier.height(20.dp)) // 🌟 高度减小，不再有过大留白

                                HorizontalDivider(
                                    thickness = 2.dp, // 🌟 线条加粗
                                    color = customTextColor.copy(alpha = 0.51f) // 稍微加深透明度，配合加粗效果
                                )

                                Spacer(modifier = Modifier.height(12.dp)) // 🌟 进一步缩减页脚上方的空间

                                // ------------------------------------
                                // ⑥ 居中专属页脚
                                // ------------------------------------
                                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
                                Text(
                                    text = "来自 BookNote · $dateStr",
                                    color = customTextColor.copy(alpha = 0.51f),
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