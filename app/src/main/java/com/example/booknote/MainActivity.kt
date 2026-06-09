package com.example.booknote

import kotlinx.coroutines.launch
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

// 核心修复：直接引入系统为你自动生成的主题，解决主题重复报错
import com.example.booknote.ui.theme.BookNoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BookNoteTheme {
                BookNoteApp()
            }
        }
    }
}

@Composable
fun BookNoteApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("booknote_prefs", Context.MODE_PRIVATE) }
    var showDate by remember { mutableStateOf(sharedPref.getBoolean("show_date", true)) }

    // 【终极完美版】使用 SharedPreferences 精准判定“首次安装”，并修正拼写错误
    val notes = remember {
        mutableStateListOf<Note>().apply {
            val localNotes = loadNotesFromDisk(context)
            addAll(localNotes)

            // 1. 读取首次打开标志位
            val isFirstLaunch = sharedPref.getBoolean("is_first_launch", true)

            if (isFirstLaunch) {
                // 2. 注入新手指南
                val welcomeNote = Note(
                    title = "💡 BookNote 极速上手指南 (阅读后可左滑删除)",
                    content = "欢迎使用 BookNote！这里是为你准备的极速指南，助你快速玩转所有高阶功能：\n\n" +
                            "1. 【主页功能】\n" +
                            "• 核心视觉：你记录的所有笔记都会在这里按年份自动生成圆角气泡进行精美分组。\n" +
                            "• 快捷操作：直接在主页把本条卡片【向左滑动】，即可悬停露出绿色的【归档】和红色的【删除】按钮。\n\n" +
                            "2. 【新建与编辑】\n" +
                            "• 创建笔记：点击底部导航栏最右侧的“+”号按钮即可立刻开始记录。\n" +
                            "• 快捷工具：键盘工具栏支持一键在光标处插入 '()'、添加多达9张相册图片。\n" +
                            "• 撤销反悔：工具栏支持【撤销上一步】与【重做下一步】的双箭头，打错字随时反悔。\n" +
                            "• 精准列表：长按滑动选中某几行文本，点击最左侧的【列表图标】，即可精准为选中行开头追加圆点 • 符号。\n\n" +
                            "3. 【Settings 设置】\n" +
                            "• 0内存占用：在设置页点击“选择系统文件夹”，可将所有数据和图片写进手机外置公开盘，卸载不丢失！\n" +
                            "• 深度备份：支持一键将文本和【引用的高清图片】全部打包成 ZIP 压缩包，实现完美的跨设备换机克隆。\n\n" +
                            "💡 提示：本指南就是一条普通笔记。阅读完毕后，直接在主页对它【向左滑动】点击红色删除按钮，即可轻松删掉此笔记！",
                    createdAt = System.currentTimeMillis()
                )
                add(welcomeNote)

                // 3. 【核心修复】将错字 'those' 改为正规的 'kotlinx'，同时使用 .toList() 提交快照，确保多线程数据安全
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    saveNotesToDisk(context, this@apply.toList())
                }

                // 4. 写死标志位，以后绝不重复触发
                sharedPref.edit().putBoolean("is_first_launch", false).apply()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "home", modifier = Modifier.fillMaxSize()) {
            composable("home") { HomeScreen(notes, showDate, navController) }
            composable("settings") { SettingsScreen(notes, showDate, navController, onShowDateChange = { showDate = it; sharedPref.edit().putBoolean("show_date", it).apply() }) }
            composable("archive") { ArchiveScreen(notes, showDate, navController) }
            composable("trash") { TrashScreen(notes, showDate, navController) }
            composable("edit/{noteId}") { backStackEntry -> EditNoteScreen(navController, backStackEntry.arguments?.getString("noteId") ?: "new", notes) }
        }

        if (currentRoute == "home" || currentRoute == "settings") {
            FloatingBottomBar(navController = navController, currentRoute = currentRoute, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}