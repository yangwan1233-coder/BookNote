package com.example.booknote.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.booknote.ThemeStore

@Composable
fun BookNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val useDynamicColor = ThemeStore.isUseDynamicColor(context)
    val customPrimary = ThemeStore.getPrimaryColor(context)
    val customSecondary = ThemeStore.getSecondaryColor(context)

    // 色彩融合算法：生成 100% 绝对不透明的莫奈风格底色
    val lightBg = Color(red = customPrimary.red * 0.08f + 0.92f, green = customPrimary.green * 0.08f + 0.92f, blue = customPrimary.blue * 0.08f + 0.92f, alpha = 1f)
    val darkBg = Color(red = customPrimary.red * 0.12f + 0.08f, green = customPrimary.green * 0.12f + 0.08f, blue = customPrimary.blue * 0.12f + 0.08f, alpha = 1f)
    val lightContainer = Color(red = customPrimary.red * 0.25f + 0.75f, green = customPrimary.green * 0.25f + 0.75f, blue = customPrimary.blue * 0.25f + 0.75f, alpha = 1f)
    val darkContainer = Color(red = customPrimary.red * 0.35f + 0.15f, green = customPrimary.green * 0.35f + 0.15f, blue = customPrimary.blue * 0.35f + 0.15f, alpha = 1f)
    val lightVariant = Color(red = customPrimary.red * 0.15f + 0.85f, green = customPrimary.green * 0.15f + 0.85f, blue = customPrimary.blue * 0.15f + 0.85f, alpha = 1f)
    val darkVariant = Color(red = customPrimary.red * 0.25f + 0.10f, green = customPrimary.green * 0.25f + 0.10f, blue = customPrimary.blue * 0.25f + 0.10f, alpha = 1f)

    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = customPrimary, onPrimary = Color.Black, secondary = customSecondary,
            background = darkBg, surface = darkBg, surfaceVariant = darkVariant,
            primaryContainer = darkContainer, onPrimaryContainer = Color.White,
            secondaryContainer = darkContainer, onSecondaryContainer = Color.White
        )
        else -> lightColorScheme(
            primary = customPrimary, onPrimary = Color.White, secondary = customSecondary,
            background = lightBg, surface = lightBg, surfaceVariant = lightVariant,
            primaryContainer = lightContainer, onPrimaryContainer = Color.Black,
            secondaryContainer = lightContainer, onSecondaryContainer = Color.Black
        )
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}