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
    val noteTextColorHex: Long = 0xFF000000, // 👈 新增：笔记字体颜色 (注意逗号的位置)
    val isStatusBarIconInverted: Boolean = false // 👈 状态栏
)

class ThemeSettingsManager(private val context: Context) {

    suspend fun updateStatusBarIconInverted(isInverted: Boolean) {
        context.dataStore.edit { it[KEY_INVERT_STATUS_BAR] = isInverted }
    }

    companion object {
        val KEY_THEME_MODE = intPreferencesKey("theme_mode")
        val KEY_BG_TYPE = intPreferencesKey("bg_type")
        val KEY_BG_COLOR = longPreferencesKey("bg_color")
        val KEY_BG_IMAGE_URI = stringPreferencesKey("bg_image_uri")
        val KEY_NOTE_TEXT_COLOR = longPreferencesKey("note_text_color") // 👈 新增 Key
        val KEY_INVERT_STATUS_BAR = booleanPreferencesKey("invert_status_bar_icon")
    }

    val themeStateFlow: Flow<AppThemeState> = context.dataStore.data.map { prefs ->
        AppThemeState(
            themeMode = ThemeMode.values()
                .getOrElse(prefs[KEY_THEME_MODE] ?: 0) { ThemeMode.SYSTEM },
            bgType = BackgroundType.values()
                .getOrElse(prefs[KEY_BG_TYPE] ?: 0) { BackgroundType.DEFAULT },
            bgColorHex = prefs[KEY_BG_COLOR] ?: 0xFFF5F5F5,
            bgImageUri = prefs[KEY_BG_IMAGE_URI] ?: "",
            noteTextColorHex = prefs[KEY_NOTE_TEXT_COLOR] ?: 0xFF000000, // 👈 注意这里要加逗号
            isStatusBarIconInverted = prefs[KEY_INVERT_STATUS_BAR] ?: false // 🌟 关键修复：必须在这里读取存储的值！
        )
    }

    // ... 下面是你原有的其他 update 函数，保持不动 ...

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

    // 👇 就在上面这个函数的下方，新增下面这三行代码：
    suspend fun updateNoteTextColor(colorHex: Long) {
        context.dataStore.edit { it[KEY_NOTE_TEXT_COLOR] = colorHex }
    }

    suspend fun updateBackgroundImage(uri: String) {
        context.dataStore.edit {
            it[KEY_BG_IMAGE_URI] = uri
            it[KEY_BG_TYPE] = BackgroundType.IMAGE.ordinal
        }
    }
}
