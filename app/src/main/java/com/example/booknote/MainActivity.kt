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

            // 🌟 1. 实时监控当前所在的路由页面
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route ?: ""

            // 🌟 2. 精准动态控制：只有在进入“编辑页面 (edit/{noteId})”时才给背景加上 15.dp 模糊，其余界面保持原图 (0.dp)
            val dynamicBlur = if (currentRoute.startsWith("edit/")) 15.dp else 0.dp

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? android.app.Activity)?.window
                    if (window != null) {
                        val insetsController = WindowCompat.getInsetsController(window, view)
                        val defaultSystemDark = when (themeState.themeMode) {
                            ThemeMode.LIGHT -> false
                            ThemeMode.DARK -> true
                            ThemeMode.SYSTEM -> view.context.resources.configuration.uiMode and
                                    Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
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

            // 🌟 3. 将计算好的 dynamicBlur 传给 AppBackgroundContainer
            AppBackgroundContainer(themeState = themeState, blurRadius = dynamicBlur) {
                DisposableEffect(navController) {
                    globalNavController = navController
                    onDispose {
                        globalNavController = null
                    }
                }

                BookNoteApp(navController)
            }
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

    val hazeState = remember { HazeState() }
    val themeManager = remember { ThemeSettingsManager(context) }
    val themeState by themeManager.themeStateFlow.collectAsState(initial = AppThemeState())

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier
                .fillMaxSize()
                .haze(state = hazeState),
            // === 🌟 撤销 Fade，完全恢复为你最初最流畅的华丽滑动转场 ===
            enterTransition = {
                val initialRoute = initialState.destination.route?.lowercase() ?: ""
                val targetRoute = targetState.destination.route?.lowercase() ?: ""

                if (initialRoute.contains("home") && targetRoute.contains("settings")) {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(300))
                } else if (initialRoute.contains("settings") && targetRoute.contains("home")) {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(300))
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

                if (initialRoute.contains("home") && targetRoute.contains("settings")) {
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(300))
                } else if (initialRoute.contains("settings") && targetRoute.contains("home")) {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(300))
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

                if (initialRoute.contains("settings") && targetRoute.contains("home")) {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(300))
                } else if (initialRoute.contains("home") && targetRoute.contains("settings")) {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(300))
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

                if (initialRoute.contains("settings") && targetRoute.contains("home")) {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(300))
                } else if (initialRoute.contains("home") && targetRoute.contains("settings")) {
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(300))
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(300))
                }
            }
        ) {
            // ==================================================================
            // 🌟 路由注册表升级：将 noteViewModel 传入需要修改数据的页面
            // ==================================================================
            composable("home") {
                val actContext = LocalContext.current

                // 返回桌面机制：拦截物理返回键，模拟按下系统“Home”键
                BackHandler {
                    val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
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
                SettingsScreen(notes, showDate, navController, noteViewModel) { showDate = it }
            }

            composable("archive") {
                // 注入 noteViewModel 供恢复归档
                ArchiveScreen(notes, showDate, navController, noteViewModel)
            }

            composable("trash") {
                // 注入 noteViewModel 供清空/恢复废纸篓
                TrashScreen(notes, showDate, navController, noteViewModel)
            }

            composable("todo_screen") {
                TodoScreen(navController)
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

        // 🔮 底部导航栏逻辑保留
        if (currentRoute == "home" || currentRoute == "settings") {
            if (themeState.isLiquidNavEnabled) {
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    LiquidGlassNavigationBar(
                        items = listOf(
                            BottomNavItem("home", "首页", Icons.Filled.Home, Icons.Outlined.Home),
                            BottomNavItem("settings", "设置", Icons.Filled.Settings, Icons.Outlined.Settings)
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
            } else {
                FloatingBottomBar(
                    navController = navController,
                    currentRoute = currentRoute,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}