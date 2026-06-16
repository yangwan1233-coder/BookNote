package com.example.booknote

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.booknote.ui.theme.BookNoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    // 【修复规范 1】：更名并规范化使用，防止隐性内存泄漏
    private var globalNavController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BookNoteTheme {
                val navController = rememberNavController()
                globalNavController = navController
                BookNoteApp(navController)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 【修复规范 2】：更安全、符合 Kotlin 习惯的空安全跳转调用
        intent?.getStringExtra("shortcut_target")?.let { target ->
            try {
                globalNavController?.navigate(target) {
                    launchSingleTop = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 【修复规范 3】：Activity 销毁时切断引用，彻底杜绝内存泄漏
        globalNavController = null
    }
}

@Composable
fun BookNoteApp(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("booknote_prefs", Context.MODE_PRIVATE) }
    var showDate by remember { mutableStateOf(sharedPref.getBoolean("show_date", true)) }

    // 这里只做状态的声明，绝对不能直接在这里读写文件！
    val notes = remember { mutableStateListOf<Note>() }

    // 【核心修复】：挂载协程引擎！在后台线程 (IO) 读写文件，防主线程卡顿 & 解决加载不出来的问题
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val localNotes = loadNotesFromDisk(context)
            val prefs = context.getSharedPreferences("booknote_prefs", Context.MODE_PRIVATE)

            val savedGuideVersion = prefs.getInt("guide_version", 0)
            // 💡 升级控制：将此处版本号更新为 3，强制所有机器更新新版笔记！
            // 以后每次您修改内置笔记的内容，只需要把这个数字 +1 即可。
            val currentGuideVersion = 3

            val finalNotes = mutableListOf<Note>()

            if (savedGuideVersion < currentGuideVersion) {
                // 【覆盖安装/首次安装 更新逻辑】
                // 1. 过滤掉旧版指南，保留用户的真实心血笔记
                val userRealNotes = localNotes.filterNot { it.title.contains("BookNote 极速上手指南") }

                // 2. 构建最新版指南
                // 【修复规范 4】：使用 Kotlin 极简原生字符串 """ ，彻底告别加号拼接的乱码噩梦
                val welcomeNote = Note(
                    title = "💡 BookNote 极速上手指南 (阅读后可左滑删除)",
                    content = """
                        欢迎使用 BookNote！这里是为你准备的极速指南，助你快速玩转这款旗舰级备忘录：

                        联系作者 邮箱：“3363099285@qq.com（反馈与建议请联系）” 酷安Id：“Yangwan1233” GitHub官方仓库地址：“https://github.com/yangwan1233-coder/BookNote”，感谢支持。

                        1. 【🎨 视觉与顶级动画操作】
                        • 沉浸视觉：全面穿透状态栏，笔记按年份自动生成悬浮气泡精美分组。
                        • 物理级滑动：新版本深度优化了全局滑动动画逻辑！直接在主页对本条卡片【向左滑动】，归档与删除按钮将与卡片同频丝滑滑出，带有物理阻尼回弹，带来极致解压的操作体验。

                        2. 【📝 沉浸式图文编辑】
                        • 智能防呆：离开编辑页实时自动保存；什么都没写的空白文档会自动无痕销毁。
                        • 时光倒流：底部的胶囊工具栏支持【撤销】与【重做】双向历史栈，打错字随时反悔。
                        • 精准排版：长按滑动选中任意几行文本，点击【列表图标】，即可精准为选中行追加圆点 • 符号。还支持一键在光标闪烁处插入 ()。
                        • 顶级画廊：支持插入多达9张图片。点击图片可进入【全屏画廊】左右滑动查看；点击底部可一键唤醒【系统原生级裁剪与修图】；长按图片即可快捷删除。

                        3. 【🎯 极速待办与桌面魔法 (New!)】
                        • 桌面小部件：长按手机桌面即可添加 BookNote 专属的 2x2 待办小部件，实现 App 与桌面的亚毫秒级双向同步！
                        • ⚠️ 使用提醒：添加前请务必先在系统设置中允许本 App【添加桌面快捷方式】。
                        • 激活同步：首次添加小部件后，请在 App 内随便点击完成两条待办事项，桌面小部件即可成功唤醒并同步显示！（注：目前桌面小部件的显示效果仍在暴走优化中，敬请期待）

                        4. 【🗂️ 极客级数据与隐私】
                        • 0 内存占用：前往 Settings 绑定“系统公开文件夹”，所有文本和超清原图将直接存入你指定的系统盘，App卸载数据绝不丢失！
                        • 图库防污染：底层独家植入 .nomedia 隐私黑科技，你的私密笔记图片绝对不会被手机系统图库扫描和暴露。
                        • 聚合备份舱：在 Settings 界面点击【数据备份与恢复】胶囊按钮，面板会在原地丝滑展开。支持一键 ZIP 完整克隆（含图片）与文本导入。再次点击右上角“云朵图标”或直接按【手机物理返回键】即可优雅关闭面板。

                        5. 【⚙️ 莫奈级个性化主题】
                        • 全局色彩引擎：在 Settings 界面点击【个性化主题配色】，面板将动态延展成一张大卡片。内置 5 款高级莫奈印象派预设，更支持 RGB 双滑轮深度定制全局色彩，或一键恢复“系统壁纸动态取色”。
                        • 沉浸式交互：选色面板同样支持内联展开与闭合，调试完毕后，点击右上角“调色盘图标”或按下【手机物理返回键】即可丝滑收起，绝不打断你的使用心流。

                        💡 提示：本指南就是一条普通笔记，阅读完毕后直接【向左滑动】点击红色删除按钮即可销毁它！关于其他功能的意见或 Bug 欢迎随时反馈，后续将持续爆肝更新，感谢你的陪伴！
                    """.trimIndent(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                // 3. 始终确保将指南排在第 0 位（最上面）
                finalNotes.add(welcomeNote)
                finalNotes.addAll(userRealNotes)

                // 4. 将更新后的数据落盘，并刷新版本号
                saveNotesToDisk(context, finalNotes)
                prefs.edit().putInt("guide_version", currentGuideVersion).apply()

            } else {
                // 【日常打开逻辑】没有发现新版本，直接沿用本地数据
                finalNotes.addAll(localNotes)
            }

            // 【至关重要的一步】：必须切回主线程去更新 Compose 的 State 变量，确保 UI 百分之百响应更新！
            withContext(Dispatchers.Main) {
                notes.clear()
                notes.addAll(finalNotes)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                val initialRoute = initialState.destination.route?.lowercase() ?: ""
                val targetRoute = targetState.destination.route?.lowercase() ?: ""

                if (initialRoute.contains("home") && targetRoute.contains("settings")) {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300))
                } else if (initialRoute.contains("settings") && targetRoute.contains("home")) {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300))
                } else {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300))
                }
            },
            exitTransition = {
                val initialRoute = initialState.destination.route?.lowercase() ?: ""
                val targetRoute = targetState.destination.route?.lowercase() ?: ""

                if (initialRoute.contains("home") && targetRoute.contains("settings")) {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300))
                } else if (initialRoute.contains("settings") && targetRoute.contains("home")) {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300))
                } else {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300))
                }
            },
            popEnterTransition = {
                val initialRoute = initialState.destination.route?.lowercase() ?: ""
                val targetRoute = targetState.destination.route?.lowercase() ?: ""

                if (initialRoute.contains("settings") && targetRoute.contains("home")) {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300))
                } else if (initialRoute.contains("home") && targetRoute.contains("settings")) {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300))
                } else {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300))
                }
            },
            popExitTransition = {
                val initialRoute = initialState.destination.route?.lowercase() ?: ""
                val targetRoute = targetState.destination.route?.lowercase() ?: ""

                if (initialRoute.contains("settings") && targetRoute.contains("home")) {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300))
                } else if (initialRoute.contains("home") && targetRoute.contains("settings")) {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300))
                } else {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300))
                }
            }
        ) {
            composable("home") { HomeScreen(notes, showDate, navController) }
            composable("settings") { SettingsScreen(notes, showDate, navController, onShowDateChange = { showDate = it; sharedPref.edit().putBoolean("show_date", it).apply() }) }
            composable("archive") { ArchiveScreen(notes, showDate, navController) }
            composable("trash") { TrashScreen(notes, showDate, navController) }
            composable("edit/{noteId}") { backStackEntry -> EditNoteScreen(navController, backStackEntry.arguments?.getString("noteId") ?: "new", notes) }
            composable("todo_screen") { TodoScreen(navController) }
            composable("more_settings_screen") { MoreSettingsScreen(navController) }
        }

        // 底部导航栏逻辑完美保留
        if (currentRoute == "home" || currentRoute == "settings") {
            FloatingBottomBar(navController = navController, currentRoute = currentRoute, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}