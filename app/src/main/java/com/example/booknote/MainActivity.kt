package com.example.booknote

import android.app.Activity
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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.navigation.NavBackStackEntry
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.ui.unit.dp
// 🌟 引入 ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private var globalNavController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val themeManager = remember { ThemeSettingsManager(context) }
            val themeState by themeManager.themeStateFlow.collectAsState(initial = AppThemeState())

            val navController = rememberNavController()

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? android.app.Activity)?.window
                    if (window != null) {
                        val insetsController = WindowCompat.getInsetsController(window, view)
                        val defaultSystemDark = when (themeState.themeMode) {
                            ThemeMode.LIGHT -> false
                            ThemeMode.DARK -> true
                            ThemeMode.SYSTEM -> view.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                        }
                        val defaultLightStatusBars = !defaultSystemDark
                        insetsController.isAppearanceLightStatusBars = if (themeState.isStatusBarIconInverted) {
                            !defaultLightStatusBars
                        } else {
                            defaultLightStatusBars
                        }
                    }
                }
            }

            DisposableEffect(navController) {
                globalNavController = navController
                onDispose {
                    globalNavController = null
                }
            }

            // 🌟 拔掉这里的背景容器，直接调用 App，让 App 自己去管理 Z 轴层级！
            BookNoteApp(navController)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
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
        globalNavController = null
    }
}

@Composable
fun BookNoteApp(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    val sharedPref =
        remember { context.getSharedPreferences("booknote_prefs", Context.MODE_PRIVATE) }
    var showDate by remember { mutableStateOf(sharedPref.getBoolean("show_date", true)) }

    // ==================================================================
    // 🌟 核心状态收编：大厂级 MVVM 架构
    // 彻底淘汰本地的 MutableList，改为只读订阅 ViewModel 提供的状态流
    // ==================================================================
    val noteViewModel: NoteViewModel = viewModel()
    val notes by noteViewModel.notesState.collectAsState()

    // 🌟 1. 在这里创建照相机
    val hazeState = remember { HazeState() }
    val themeManager = remember { ThemeSettingsManager(context) }
    val themeState by themeManager.themeStateFlow.collectAsState(initial = AppThemeState())

    // 🌟 2. 动态模糊的逻辑移到这里
    val dynamicBlur = if (currentRoute?.startsWith("edit/") == true) 15.dp else 0.dp
    // 🌟 3. 最外层根容器 (包含底层的背景/内容，以及顶层的悬浮导航栏)
    Box(modifier = Modifier.fillMaxSize()) {

        Box(modifier = Modifier.fillMaxSize().haze(state = hazeState)) {

            // 铺上自定义背景
            AppBackgroundContainer(themeState = themeState, blurRadius = dynamicBlur) {

                // 原本透明的 NavHost 放进背景容器里
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier.fillMaxSize(),
                    // === 🌟 核心修复：精简 Haze 容器下的转场动画 ===
                    enterTransition = {
                        val initialRoute = initialState.destination.route?.lowercase() ?: ""
                        val targetRoute = targetState.destination.route?.lowercase() ?: ""

                        // 移除滑动，仅保留淡入淡出，极大减轻 GPU 在模糊运算时的负担
                        if ((initialRoute.contains("home") && targetRoute.contains("settings")) ||
                            (initialRoute.contains("settings") && targetRoute.contains("home"))
                        ) {
                            fadeIn(tween(300, easing = FastOutSlowInEasing))
                        } else {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            ) + fadeIn(tween(300))
                        }
                    },
                    exitTransition = {
                        val initialRoute = initialState.destination.route?.lowercase() ?: ""
                        val targetRoute = targetState.destination.route?.lowercase() ?: ""

                        if ((initialRoute.contains("home") && targetRoute.contains("settings")) ||
                            (initialRoute.contains("settings") && targetRoute.contains("home"))
                        ) {
                            fadeOut(tween(300, easing = FastOutSlowInEasing))
                        } else {
                            slideOutHorizontally(
                                targetOffsetX = { -it },
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            ) + fadeOut(tween(300))
                        }
                    },
                    popEnterTransition = {
                        val initialRoute = initialState.destination.route?.lowercase() ?: ""
                        val targetRoute = targetState.destination.route?.lowercase() ?: ""

                        if ((initialRoute.contains("settings") && targetRoute.contains("home")) ||
                            (initialRoute.contains("home") && targetRoute.contains("settings"))
                        ) {
                            fadeIn(tween(300, easing = FastOutSlowInEasing))
                        } else {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            ) + fadeIn(tween(300))
                        }
                    },
                    popExitTransition = {
                        val initialRoute = initialState.destination.route?.lowercase() ?: ""
                        val targetRoute = targetState.destination.route?.lowercase() ?: ""

                        if ((initialRoute.contains("settings") && targetRoute.contains("home")) ||
                            (initialRoute.contains("home") && targetRoute.contains("settings"))
                        ) {
                            fadeOut(tween(300, easing = FastOutSlowInEasing))
                        } else {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            ) + fadeOut(tween(300))
                        }
                    }
                ) {
                    // ... 底下的 composable 路由保持不变 ...
                    // ==================================================================
                    // 🌟 路由注册表升级：将 noteViewModel 传入需要修改数据的页面
                    // ==================================================================
                    composable("home") {
                        val actContext = LocalContext.current

                        // 返回桌面机制：拦截物理返回键，模拟按下系统“Home”键
                        BackHandler {
                            val intent =
                                android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                                    addCategory(android.content.Intent.CATEGORY_HOME)
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                            actContext.startActivity(intent)
                        }

                        // 注入 noteViewModel 供操作数据
                        HomeScreen(notes, showDate, navController, noteViewModel)
                    }

                    composable("settings") {
                        // 🌟 新增：把 noteViewModel 传给设置页，让它接管恢复备份和导入操作
                        SettingsScreen(notes, showDate, navController, noteViewModel) {
                            showDate = it
                        }
                    }

                    composable("archive") {
                        // 注入 noteViewModel 供恢复归档，并传入 themeState 供双态切换
                        ArchiveScreen(
                            notes = notes,
                            showDate = showDate,
                            navController = navController,
                            noteViewModel = noteViewModel,
                            themeState = themeState // 🌟 核心补全：传入主题状态
                        )
                    }

                    composable("trash") {
                        // 注入 noteViewModel 供清空/恢复废纸篓，并传入 themeState 供双态切换
                        TrashScreen(
                            notes = notes,
                            showDate = showDate,
                            navController = navController,
                            noteViewModel = noteViewModel,
                            themeState = themeState // 🌟 核心补全：传入主题状态
                        )
                    }

                    composable("todo_screen") {
                        TodoScreen(
                            navController = navController,
                            // ... 其他参数 ...
                            themeState = themeState // 🌟 新增这一行：把外层的 themeState 传递进去
                        )
                    }

                    composable("more_settings_screen") {
                        MoreSettingsScreen(navController)
                    }

                    composable("edit/{noteId}") { backStackEntry ->
                        val noteId = backStackEntry.arguments?.getString("noteId") ?: ""
                        // 注入 noteViewModel 供保存笔记
                        EditNoteScreen(navController, noteId, notes, noteViewModel)
                    }
                }
            }
        }

                // 🔮 底部导航栏逻辑保留
                if (currentRoute == "home" || currentRoute == "settings") {
                    if (themeState.isLiquidNavEnabled) {
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                            BookNoteTheme {
                                LiquidGlassNavigationBar(
                                    items = listOf(
                                        BottomNavItem(
                                            "home",
                                            "首页",
                                            Icons.Filled.Home,
                                            Icons.Outlined.Home
                                        ),
                                        BottomNavItem(
                                            "settings",
                                            "设置",
                                            Icons.Filled.Settings,
                                            Icons.Outlined.Settings
                                        )
                                    ),
                                    currentRoute = currentRoute,
                                    onNavigate = { route ->
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onAddClick = {
                                        navController.navigate("edit/new")
                                    },
                                    hazeState = hazeState
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                            BookNoteTheme {
                                FloatingBottomBar(
                                    navController = navController,
                                    currentRoute = currentRoute
                                    // 注意：align 属性已经安全转移给外层 Box，这里不再需要传 modifier
                                )
                            }
                        }
                    }
                }
            }
}
