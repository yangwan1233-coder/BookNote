package com.example.booknote // 👈 请保持您原有的 package 路径

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
import coil.compose.rememberAsyncImagePainter
import com.example.booknote.ui.theme.BookNoteTheme

@Composable
fun AppBackgroundContainer(
    themeState: AppThemeState,
    content: @Composable () -> Unit
) {
    // 1. 判断是否开启深色模式
    val isDark = when (themeState.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // 2. 保持原生 MaterialTheme，不强制重置按钮色彩系统（防止按钮变黑）
    BookNoteTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            // 3. 渲染底层背景：仅改变背景色彩，不干扰按钮组件
            when {
                // 如果开启了深色模式，且未强制指定自定义图片/纯色背景，则纯背景变黑色/极深灰
                isDark && themeState.bgType == BackgroundType.DEFAULT -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF121212)) // 👈 只把背景变黑，不改变按钮颜色
                    )
                }

                // 用户自定义纯色背景
                themeState.bgType == BackgroundType.COLOR -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(themeState.bgColorHex))
                    )
                }

                // 用户自定义图片背景
                themeState.bgType == BackgroundType.IMAGE -> {
                    if (themeState.bgImageUri.isNotBlank()) {
                        Image(
                            painter = rememberAsyncImagePainter(themeState.bgImageUri),
                            contentDescription = "App Global Background",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (isDark) Color(0xFF121212) else MaterialTheme.colorScheme.background)
                        )
                    }
                }

                // 默认浅色背景
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                }
            }

            // 4. 渲染顶层 UI 内容
            content()
        }
    }
}