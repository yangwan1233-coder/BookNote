package com.example.booknote

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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

    val notes = remember {
        mutableStateListOf<Note>().apply {
            val localNotes = loadNotesFromDisk(context)
            addAll(localNotes)

            // 【核心修改】抛弃首次安装标志位，直接判断：只要当前本地没有笔记（如刚安装），就注入引导笔记
            if (localNotes.isEmpty()) {
                val welcomeNote = Note(
                    title = "💡 BookNote 极速上手指南 (阅读后可左滑删除)",
                    content = "欢迎使用 BookNote！这里是为你准备的极速指南，助你快速玩转这款旗舰级备忘录：\n\n" +
                            "联系作者 邮箱：‘3363099285@qq.com’ 酷安Id：‘Yangwan1233’ GitHub官方仓库地址：‘https://github.com/yangwan1233-coder/BookNote’，感谢支持" +
                            "1. 【🎨 视觉与主页操作】\n" +
                            "• 沉浸视觉：全面穿透状态栏，笔记按年份自动生成悬浮气泡精美分组。\n" +
                            "• 物理级滑动：直接在主页对本条卡片【向左滑动】，即可体验带有物理阻尼回弹的悬停菜单，一键【归档】或【删除】。\n\n" +
                            "2. 【📝 沉浸式图文编辑】\n" +
                            "• 智能防呆：离开编辑页实时自动保存；什么都没写的空白文档会自动无痕销毁。\n" +
                            "• 时光倒流：底部的胶囊工具栏支持【撤销】与【重做】双向历史栈，打错字随时反悔。\n" +
                            "• 精准排版：长按滑动选中任意几行文本，点击【列表图标】，即可精准为选中行追加圆点 • 符号。还支持一键在光标闪烁处插入 ()。\n" +
                            "• 顶级画廊：支持插入多达9张图片。点击图片可进入【全屏画廊】左右滑动查看；点击底部可一键唤醒【系统原生级裁剪与修图】；长按图片即可快捷删除。\n\n" +
                            "3. 【🗂️ 极客级数据与隐私】\n" +
                            "• 0 内存占用：前往 Settings 绑定“系统公开文件夹”，所有文本和超清原图将直接存入你指定的系统盘，App卸载数据也绝不丢失！\n" +
                            "• 图库防污染：底层独家植入 .nomedia 隐私黑科技，你的私密笔记图片绝对不会被手机系统图库扫描和暴露。\n" +
                            "• 换机克隆：支持一键将文本和【所有引用的高清图片】完整打包成 ZIP 压缩包，跨设备完美恢复！\n\n" +
                            "💡 提示：本指南就是一条普通笔记。阅读完毕后，直接在主页对它【向左滑动】点击红色删除按钮，即可轻松销毁它！",
                    createdAt = System.currentTimeMillis()
                )
                add(welcomeNote)

                // 异步将这条笔记写入本地存储，使之真正留存在手机里
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    saveNotesToDisk(context, this@apply.toList())
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 【核心修改点】给 NavHost 注入了四大原生的阻尼丝滑转场动画，页面跳转不再生硬！
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize(),
            // 1. 打开新页面：从右侧轻微滑入 + 渐显
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { 300 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(300))
            },
            // 2. 旧页面退居后台：向左侧轻微滑出 + 渐隐
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -300 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(300))
            },
            // 3. 按返回键回到旧页面：从左侧轻微滑入 + 渐显
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -300 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(300))
            },
            // 4. 按返回键关闭当前页面：向右侧轻微滑出 + 渐隐
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { 300 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            composable("home") { HomeScreen(notes, showDate, navController) }
            composable("settings") { SettingsScreen(notes, showDate, navController, onShowDateChange = { showDate = it; sharedPref.edit().putBoolean("show_date", it).apply() }) }
            composable("archive") { ArchiveScreen(notes, showDate, navController) }
            composable("trash") { TrashScreen(notes, showDate, navController) }
            composable("edit/{noteId}") { backStackEntry -> EditNoteScreen(navController, backStackEntry.arguments?.getString("noteId") ?: "new", notes) }
        }

        // 这里的底部导航栏悬浮逻辑完美为您保留，未做任何改动
        if (currentRoute == "home" || currentRoute == "settings") {
            FloatingBottomBar(navController = navController, currentRoute = currentRoute, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}