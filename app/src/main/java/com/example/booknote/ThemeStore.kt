package com.example.booknote

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

object ThemeStore {
    // 🎨 莫奈经典印象派配色预设 (绝对实心纯色)
    val MonetWaterLily = Color(0xFF7D9B9F)
    val MonetSunrise = Color(0xFFE8B49A)
    val MonetLavender = Color(0xFF9D8CBA)
    val MonetHaystacks = Color(0xFFE6C875)
    val MonetGarden = Color(0xFF88A681)

    fun setUseDynamicColor(context: Context, useDynamic: Boolean) {
        context.getSharedPreferences("booknote_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("use_dynamic_color", useDynamic).apply()
    }

    fun isUseDynamicColor(context: Context): Boolean {
        return context.getSharedPreferences("booknote_prefs", Context.MODE_PRIVATE)
            .getBoolean("use_dynamic_color", true)
    }

    fun saveColors(context: Context, primary: Color, secondary: Color) {
        context.getSharedPreferences("booknote_prefs", Context.MODE_PRIVATE).edit()
            .putInt("theme_primary", primary.copy(alpha = 1f).toArgb())
            .putInt("theme_secondary", secondary.copy(alpha = 1f).toArgb())
            .putBoolean("use_dynamic_color", false) // 保存自定义色即关闭壁纸跟随
            .apply()
    }

    fun getPrimaryColor(context: Context): Color {
        val prefs = context.getSharedPreferences("booknote_prefs", Context.MODE_PRIVATE)
        return Color(prefs.getInt("theme_primary", MonetWaterLily.toArgb())).copy(alpha = 1f)
    }

    fun getSecondaryColor(context: Context): Color {
        val prefs = context.getSharedPreferences("booknote_prefs", Context.MODE_PRIVATE)
        return Color(prefs.getInt("theme_secondary", MonetSunrise.toArgb())).copy(alpha = 1f)
    }
}