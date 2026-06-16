package com.example.booknote

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.color.*
import androidx.glance.layout.*
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ================= 1. 规范化主题管理 =================
object WidgetTheme {
    val bgMain = ColorProvider(day = Color(0xFFF5F5F5), night = Color(0xFF121212))
    val bgHeader = ColorProvider(day = Color(0xFFFFD54F), night = Color(0xFFFBC02D))
    val textTitle = ColorProvider(day = Color(0xFF222222), night = Color.White)
    val textNormal = ColorProvider(day = Color.Black, night = Color.White)
    val textHint = ColorProvider(day = Color.Gray, night = Color.LightGray)
    val bgCream = ColorProvider(day = Color(0xFFFFF9E6), night = Color(0xFF2C2B26))
    val divider = ColorProvider(day = Color(0x20000000), night = Color(0x20FFFFFF))
}

data class WidgetTodoItem(val id: String, val content: String)

// ================= 2. 核心渲染引擎 =================
class TodoWidget : GlanceAppWidget() {

    // 关键：定义状态存储，这是实现“自动同步”而不只是“快捷方式”的核心
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    companion object {
        val DATA_KEY = stringPreferencesKey("todo_list_json")
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // 1. 动态尺寸检测 (Keep)
            val size = LocalSize.current
            
            /**
             * 【核心修复】：动态间距补偿逻辑
             * 在小米/三星等系统上，即使代码写了 170dp，系统分给小部件的实际宽度可能只有 150dp 甚至更小。
             * 如果死锁 12dp 间距，在这些“缩水”后的空间里会显得极其拥挤。
             */
            val isShrunk = size.width < 170.dp
            // 如果空间被系统压缩，按比例缩小边距（最小不低于 6dp），否则保持 12dp 大边距
            // 修正 Dp 乘法计算：(当前宽 / 预期宽) * 12dp
            val dynamicPadding = if (isShrunk) {
                val ratio = size.width.value / 170f
                maxOf(6.dp, (ratio * 12).dp)
            } else {
                12.dp
            }

            val headerPadding = dynamicPadding
            val btnPadding = dynamicPadding

            val prefs = currentState<Preferences>()
            val json = prefs[DATA_KEY] ?: ""
            
            // 解析状态中的数据
            val top3Todos: List<WidgetTodoItem> = if (json.isEmpty()) {
                emptyList()
            } else {
                val type = object : TypeToken<List<WidgetTodoItem>>() {}.type
                Gson().fromJson(json, type)
            }

            val mainIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("shortcut_target", "todo_screen")
            }

            Column(
                modifier = GlanceModifier
                    .size(162.dp) // 3. 恢复固定尺寸 (Reverted)
                    .background(WidgetTheme.bgMain)
                    .cornerRadius(26.dp) 
                    .clickable(actionStartActivity(mainIntent))
            ) {
                // Header (高度 38dp)
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(38.dp) 
                        .background(WidgetTheme.bgHeader)
                        .padding(horizontal = headerPadding), // 应用动态间距 (12.dp for Large)
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "待办事项", 
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WidgetTheme.textTitle)
                        // 移除这里的额外的 padding，统一使用 Row 的 horizontal padding 以实现 12.dp 宽大边距
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "＋",
                        style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WidgetTheme.textTitle)
                    )
                }

                // List Container
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(WidgetTheme.bgCream)
                        .padding(vertical = 0.dp)
                ) {
                    if (top3Todos.isEmpty()) {
                        Box(
                            modifier = GlanceModifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无待办",
                                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WidgetTheme.textHint)
                            )
                        }
                    } else {
                        for (i in 0 until 3) {
                            val todo = top3Todos.getOrNull(i)
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                Box(
                                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (todo != null) {
                                        TodoItemRow(text = todo.content, padding = btnPadding) // 4. 智能参数传递 (Keep)
                                    }
                                }
                                Box(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp) 
                                        .height(1.dp)
                                        .background(WidgetTheme.divider)
                                ) {}
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TodoItemRow(text: String, padding: androidx.compose.ui.unit.Dp) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 圆形按钮：使用响应式传入的间距
        Text(
            text = "〇",
            style = TextStyle(
                fontSize = 16.sp, 
                fontWeight = FontWeight.Bold, 
                color = WidgetTheme.bgHeader
            ),
            modifier = GlanceModifier.padding(start = padding, end = padding)
        )
        Text(
            text = text,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WidgetTheme.textNormal),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TodoWidget()
}
