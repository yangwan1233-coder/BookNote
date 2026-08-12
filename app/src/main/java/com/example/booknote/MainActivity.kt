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
import androidx.room.util.wrapMappedColumns
import com.example.booknote.ui.theme.BookNoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    // 【修复规范 1】：更名并规范化使用，防止隐性内存泄漏（保留原逻辑）
    private var globalNavController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // 🌟 【新增：大厂级主题与背景管理器初始化】
            val context = LocalContext.current
            val themeManager = remember { ThemeSettingsManager(context) }
            val themeState by themeManager.themeStateFlow.collectAsState(initial = AppThemeState())

            // 🌟 【新增：状态栏反色逻辑实质生效控制】
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? android.app.Activity)?.window
                    if (window != null) {
                        val insetsController = WindowCompat.getInsetsController(window, view)

                        // 1. 判断系统/当前是否为深色模式
                        val defaultSystemDark = when (themeState.themeMode) {
                            ThemeMode.LIGHT -> false
                            ThemeMode.DARK -> true
                            ThemeMode.SYSTEM -> view.context.resources.configuration.uiMode and
                                    Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                        }

                        // 2. 计算默认图标模式：深色背景用白字(false)，浅色背景用黑字(true)
                        val defaultLightStatusBars = !defaultSystemDark

                        // 3. 应用“反色”控制：开启反色时取反，关闭时保持默认
                        insetsController.isAppearanceLightStatusBars = if (themeState.isStatusBarIconInverted) {
                            !defaultLightStatusBars
                        } else {
                            defaultLightStatusBars
                        }
                    }
                }
            }

            // 🌟 【新增：用 AppBackgroundContainer 包裹整个应用】
            // 内部已自动包含 BookNoteTheme(darkTheme = isDark)，且支持全局纯色/自定义背景图片
            AppBackgroundContainer(themeState = themeState) {
                val navController = rememberNavController()

                // 【规范优化】：使用 DisposableEffect 在重组生命周期内安全绑定控制器，防止重组副作用
                DisposableEffect(navController) {
                    globalNavController = navController
                    onDispose {
                        globalNavController = null
                    }
                }

                // 您的应用主入口（原封不动）
                BookNoteApp(navController)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 【修复规范 2】：更安全、符合 Kotlin 习惯的空安全跳转调用（保留原逻辑）
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
        // 【修复规范 3】：Activity 销毁时切断引用，彻底杜绝内存泄漏（保留原逻辑）
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

    // ==================================================================
    // 1. 【原汁原味保留】：只做全局状态声明（Single Source of Truth 唯一真理源）
    // ==================================================================
    val notes = remember { mutableStateListOf<Note>() }

    // ==================================================================
    // 2. 【核心注入组件】：挂载协程异步引擎，完成磁盘数据读取与使用指南的版本灰度升级！
    // 严格满足您的逻辑：一旦改变版本号，即刻自动将包含表格与思维导图的新版使用指南
    // 优雅地以最新日期（当前最新时间戳）自然融入集合，展示在主页的最顶部！
    // ==================================================================
    val scope = rememberCoroutineScope()
    // ==================================================================
// 【大厂级优化】：挂载协程引擎！后台异步加载，自动灰度升级内置超级使用指南
// ==================================================================
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val localNotes = loadNotesFromDisk(context)
            val prefs = context.getSharedPreferences("booknote_prefs", Context.MODE_PRIVATE)

            val savedGuideVersion = prefs.getInt("guide_version", 0)

            // 💡 升级控制中心：当前指南版本更新为 4！
            // 以后只要您修改了下方的指南内容，只需将此处的数字 +1，全量机器就会自动强制更新！
            val currentGuideVersion = 9

            val finalNotes = mutableListOf<Note>()
            val currentTime = System.currentTimeMillis()

            if (savedGuideVersion < currentGuideVersion) {
                // 【覆盖安装/新版发布 灰度更新逻辑】
                // 1. 严格过滤掉以前各版本的旧指南，只保留用户的真实心血笔记，绝不误删用户数据！
                val userRealNotes = localNotes.filterNot { it.title.contains("BookNote 极速上手指南") }

                // 2. 严谨构建大厂级【三位一体】超级使用指南（包含文本、表格、思维导图）
                val guideBlocks = mutableListOf<UIBlock>()

                // 🌟 核心修改：纯本地化离线渲染（零网络请求、零延迟）
                // Coil 引擎会自动解析 android.resource:// 协议，完美渲染 drawble 图片！
                // =================================================================
                val demoImages = listOf(
                    "android.resource://${context.packageName}/${R.drawable.guide_pic_1}",
                    "android.resource://${context.packageName}/${R.drawable.guide_pic_2}",
                    "android.resource://${context.packageName}/${R.drawable.guide_pic_3}"
                )
                // 块①：欢迎辞与核心亮点描述
                val introText = """
                欢迎使用 BookNote 智能备忘录！
                （不记录来时路，何谈不负当下？）
                
                最新更新内容：全局自定义背景和笔记字体颜色，笔记预览卡片滑动动画。
                
                本应用已全面升级为大厂级“流式图文排版架构”。在这里，文字、表格与思维导图不再是孤立的组件，而是可以随心所欲自由混排的生命体！
                
            """.trimIndent()
                guideBlocks.add(UITextBlock(androidx.compose.ui.text.input.TextFieldValue(introText)))

                // =================================================================
                // 块②：使用说明数据表格（UITableBlock）
                // =================================================================
                // 【核心修复 1】：完全顺应您真实的 TableData 结构，使用 cells 构建二维数组
                val tableBlock = UITableBlock(
                    tableData = TableData(
                        title = "BookNote 核心功能表", // 可选标题
                        rows = 5, // 包含表头在内共 5 行
                        cols = 3, // 共 3 列
                        cells = listOf(
                            listOf("核心功能", "操作手势", "大厂级稳定性保障"), // 表头
                            listOf("思维导图", "底部工具栏一键插入", "节点无限延伸，防越界崩溃"),
                            listOf("智能表格", "点击插入，行列自由增删", "双轨闭环拷贝，杜绝大文件卡死"),
                            listOf("九宫格预览", "底部工具栏一键插入", "矩阵零卡顿，渲染极丝滑！"),
                            listOf("数据安全", "防杀进程自动存盘", "生命周期感知，锁死 UTF-8 防乱码")
                        )
                    )
                )
                guideBlocks.add(tableBlock)


                // 块③：过渡提示语
                val midText = "下面为您动态演示本软件的模块化思维导图架构，您可以点击任意节点直接进行向右延伸和编辑："
                guideBlocks.add(UITextBlock(androidx.compose.ui.text.input.TextFieldValue(midText)))


                // =================================================================
                // 块④：全功能内置思维导图（UIMindMapBlock）
                // =================================================================
                // 【核心修复 2】：children 是 val 常量，不可被 add，必须在构造节点时一次性嵌套传入！
                val mindMapBlock = UIMindMapBlock(
                    rootNode = MindMapNode(
                        text = "BookNote 核心宇宙",
                        // 直接在构造函数里传入子节点，优雅又安全！
                        children = listOf(
                            MindMapNode(
                                text = "⚡ 模块化排版（已打通）",
                                // 孙子节点继续向下嵌套
                                children = listOf(
                                    MindMapNode(text = "流式正文块"),
                                    MindMapNode(text = "动态数据表")
                                )
                            ),
                            MindMapNode(text = "💾 坚固持久化（已锁定）"),
                            MindMapNode(text = "🛡️ 永久图片锁（已护航）")
                        )
                    )
                )
                guideBlocks.add(mindMapBlock)

                // =================================================================
                // 🌟 新增块 A：视觉与编辑排版（采用沉浸式文本块呈现）
                // =================================================================
                val visualAndEditBlock = UITextBlock(androidx.compose.ui.text.input.TextFieldValue(
                    """
                【🎨 视觉与顶级动画操作】
                • 沉浸视觉：全面穿透状态栏，笔记按年份自动生成悬浮气泡精美分组。
                • 物理级滑动：直接在主页对本条卡片【向左滑动】，归档与删除按钮将与卡片同频丝滑滑出，带物理阻尼回弹，极致解压！
                
                【📝 沉浸式图文编辑】
                • 智能防呆：离开页面实时无感自动保存；空白文档自动无痕销毁。
                • 时光倒流：底部的胶囊工具栏支持【撤销】与【重做】双向历史栈，打错字随时反悔。
                • 精准排版：长按滑动选中任意几行文本，点击【列表图标】精准追加圆点 • 符号。还支持一键在光标处插入 ()。
                • 顶级画廊：支持插入多达 9 张图片，点击进入全屏画廊左右滑动，支持唤醒系统级裁剪与修图，长按快捷删除。
                """.trimIndent()
                ))
                guideBlocks.add(visualAndEditBlock)

                // =================================================================
                // 🌟 新增块 B：桌面魔法（采用独立高亮文本块呈现）
                // =================================================================
                val widgetBlock = UITextBlock(androidx.compose.ui.text.input.TextFieldValue(
                    """
                【🎯 极速待办与桌面魔法 (New!)】
                • 桌面小部件：长按手机桌面即可添加 BookNote 专属 2x2 待办部件，实现亚毫秒级双向同步！
                • ⚠️ 使用提醒：添加前请务必在系统设置中允许【添加桌面快捷方式】。首次添加后，请在 App 内随便完成两条待办，部件即可唤醒同步！（显示效果持续暴走优化中...）
                """.trimIndent()
                ))
                guideBlocks.add(widgetBlock)

                // =================================================================
                // 🌟 新增块 C：高阶数据与个性化（化身超酷的嵌套思维导图呈现！）
                // =================================================================
                val advanceMindMapBlock = UIMindMapBlock(
                    rootNode = MindMapNode(
                        text = "🌟 高阶玩法探索",
                        children = listOf(
                            MindMapNode(
                                text = "🗂️ 极客级数据与隐私",
                                children = listOf(
                                    MindMapNode(text = "0 内存占用 (系统盘直存)"),
                                    MindMapNode(text = ".nomedia 黑科技 (防图库污染)"),
                                    MindMapNode(text = "聚合备份舱 (ZIP丝滑克隆)")
                                )
                            ),
                            MindMapNode(
                                text = "⚙️ 莫奈级个性化主题",
                                children = listOf(
                                    MindMapNode(text = "5 款高级莫奈印象派预设"),
                                    MindMapNode(text = "RGB 双滑轮深度定制"),
                                    MindMapNode(text = "系统壁纸动态取色")
                                )
                            )
                        )
                    )
                )
                guideBlocks.add(advanceMindMapBlock)

                // =================================================================
                // 🌟 新增块 D：联系方式与开源地址（放置在指南最底部）
                // =================================================================
                val contactBlock = UITextBlock(androidx.compose.ui.text.input.TextFieldValue(
                    """
                📮 开发者与开源社区
                • 邮箱：3363099285@qq.com（反馈与建议请联系）
                • 酷安Id：Yangwan1233
                • GitHub官方仓库：https://github.com/yangwan1233-coder/BookNote
                """.trimIndent()
                ))
                guideBlocks.add(contactBlock)

                // 块⑤：结尾致谢
                val footerText = "后续版本将持续为您爆肝更新，感谢您的陪伴与信任！祝您记录愉快！"
                guideBlocks.add(UITextBlock(androidx.compose.ui.text.input.TextFieldValue(footerText)))


                // 3. 组装成合规的 Note 对象
                val welcomeNote = Note(
                    title = "💡 BookNote 极速上手指南 (阅读后可左滑删除)",
                    content = introText, // 列表纯文本预览兜底
                    imagePaths = demoImages, // 👈 纯本地图片路径列表注入！
                    // 核心改动：使用 BlockSerializer 将上述多模态区块序列化为标准的 blocksJson 注入！
                    blocksJson = BlockSerializer.serializeBlocks(guideBlocks),

                    // 💡 【大厂嵌入逻辑】：强制赋予最新的当前时间戳！
                    // 确保只要版本号发生更迭，新指南将天生具备最新日期，完美排在笔记列表的最上方！
                    createdAt = currentTime,
                    updatedAt = currentTime
                )

                // 4. 将最新指南加入队列，并顺应列表的正常时间轴自然排序
                finalNotes.add(welcomeNote)
                finalNotes.addAll(userRealNotes)

                // 5. 强制安全同步落盘，并刷新轻量记忆库中的版本号
                saveNotesToDisk(context, finalNotes)
                prefs.edit().putInt("guide_version", currentGuideVersion).apply()

            } else {
                // 【日常打开逻辑】版本号未变，直接读取本地数据
                finalNotes.addAll(localNotes)
            }

            // ==================================================================
            // 【至关重要的一步】：切回 UI 主线程安全同步，触发 Compose 状态绝对响应更新
            // ==================================================================
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