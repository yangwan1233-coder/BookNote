package com.example.booknote

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.booknote.ui.theme.BookNoteTheme

@Composable
fun AppBackgroundContainer(
    themeState: AppThemeState,
    blurRadius: Dp = 0.dp, // 🌟 新增参数：默认不模糊，可由外部动态控制
    content: @Composable () -> Unit
) {
    val isDark = when (themeState.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    BookNoteTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isDark && themeState.bgType == BackgroundType.DEFAULT -> {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212)))
                }

                themeState.bgType == BackgroundType.COLOR -> {
                    Box(modifier = Modifier.fillMaxSize().background(Color(themeState.bgColorHex)))
                }

                themeState.bgType == BackgroundType.IMAGE -> {
                    if (themeState.bgImageUri.isNotBlank()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                painter = rememberAsyncImagePainter(themeState.bgImageUri),
                                contentDescription = "App Global Background",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = 1.05f // 模糊时边缘可能会有白边，放大 5% 完美遮挡
                                        scaleY = 1.05f
                                    }
                                    // 🌟 核心：根据外部传进来的 blurRadius 动态呈现模糊度（编辑页传 15.dp，其余传 0.dp）
                                    .blur(radius = blurRadius)
                            )

                            // 智能感光蒙版
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (isDark) {
                                            Color.Black.copy(alpha = if (blurRadius > 0.dp) 0.65f else 0.6f)
                                        } else {
                                            Color.Black.copy(alpha = if (blurRadius > 0.dp) 0.3f else 0.25f)
                                        }
                                    )
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF121212) else MaterialTheme.colorScheme.background))
                    }
                }

                else -> {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                }
            }

            content()
        }
    }
}