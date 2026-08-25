package com.example.booknote

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "app_theme_settings")

// 主题模式：0-跟随系统, 1-浅色, 2-深色
enum class ThemeMode { SYSTEM, LIGHT, DARK }

// 背景类型：0-默认Material背景, 1-纯色背景, 2-图片背景
enum class BackgroundType { DEFAULT, COLOR, IMAGE }

data class AppThemeState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val bgType: BackgroundType = BackgroundType.DEFAULT,
    val bgColorHex: Long = 0xFFF5F5F5, // 默认背景色
    val bgImageUri: String = "",
    val noteTextColorHex: Long = 0xFF000000, // 👈 笔记字体颜色
    val isStatusBarIconInverted: Boolean = false, // 🌟 修复：补上了末尾的逗号
    val isLiquidNavEnabled: Boolean = true // 新增：是否开启液态玻璃导航栏
)

class ThemeSettingsManager(private val context: Context) {

    companion object {
        val KEY_THEME_MODE = intPreferencesKey("theme_mode")
        val KEY_BG_TYPE = intPreferencesKey("bg_type")
        val KEY_BG_COLOR = longPreferencesKey("bg_color")
        val KEY_BG_IMAGE_URI = stringPreferencesKey("bg_image_uri")
        val KEY_NOTE_TEXT_COLOR = longPreferencesKey("note_text_color")
        val KEY_INVERT_STATUS_BAR = booleanPreferencesKey("invert_status_bar_icon")
        // 🌟 关键修复 1：把 key 的名字加上 _v2，彻底重置所有老用户的本地缓存状态
        val KEY_LIQUID_NAV_ENABLED = booleanPreferencesKey("liquid_nav_enabled_v2")
    }

    val themeStateFlow: Flow<AppThemeState> = context.dataStore.data.map { prefs ->
        AppThemeState(
            themeMode = ThemeMode.values()
                .getOrElse(prefs[KEY_THEME_MODE] ?: 0) { ThemeMode.SYSTEM },
            bgType = BackgroundType.values()
                .getOrElse(prefs[KEY_BG_TYPE] ?: 0) { BackgroundType.DEFAULT },
            bgColorHex = prefs[KEY_BG_COLOR] ?: 0xFFF5F5F5,
            bgImageUri = prefs[KEY_BG_IMAGE_URI] ?: "",
            noteTextColorHex = prefs[KEY_NOTE_TEXT_COLOR] ?: 0xFF000000,
            isStatusBarIconInverted = prefs[KEY_INVERT_STATUS_BAR] ?: false,
            // 🌟 关键修复 2：把默认的 fallback 从 false 改成了 true
            // 这样系统如果找不到数据，或者找到了 _v2 的新 Key，都会无条件返回 true！
            isLiquidNavEnabled = prefs[KEY_LIQUID_NAV_ENABLED] ?: true
        )
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.ordinal }
    }

    suspend fun updateBackgroundType(type: BackgroundType) {
        context.dataStore.edit { it[KEY_BG_TYPE] = type.ordinal }
    }

    suspend fun updateBackgroundColor(colorHex: Long) {
        context.dataStore.edit {
            it[KEY_BG_COLOR] = colorHex
            it[KEY_BG_TYPE] = BackgroundType.COLOR.ordinal
        }
    }

    suspend fun updateNoteTextColor(colorHex: Long) {
        context.dataStore.edit { it[KEY_NOTE_TEXT_COLOR] = colorHex }
    }

    suspend fun updateBackgroundImage(uri: String) {
        context.dataStore.edit {
            it[KEY_BG_IMAGE_URI] = uri
            it[KEY_BG_TYPE] = BackgroundType.IMAGE.ordinal
        }
    }

    suspend fun updateStatusBarIconInverted(isInverted: Boolean) {
        context.dataStore.edit { it[KEY_INVERT_STATUS_BAR] = isInverted }
    }

    suspend fun updateLiquidNavEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LIQUID_NAV_ENABLED] = enabled }
    }
}