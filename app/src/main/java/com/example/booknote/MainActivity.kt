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
import dev.chrisbanes.haze.hazeSource // 🌟 替换为 1.7.2 的 hazeSource
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.layout.BoxScope

// ==================================================================
// 🌟 核心重构：声明全局通信管道 (插槽架构)
// ==================================================================
val LocalGlobalHazeState = compositionLocalOf<HazeState> { error("未提供全局 HazeState") }
val LocalFloatingUI = compositionLocalOf<MutableState<(@Composable BoxScope.() -> Unit)?>> { error("未提供悬浮层插槽") }

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

            val currentThemeMode = themeState.themeMode
            val isIconInverted = themeState.isStatusBarIconInverted

            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? android.app.Activity)?.window
                    if (window != null) {
                        val insetsController = WindowCompat.getInsetsController(window, view)

                        val defaultSystemDark = when (currentThemeMode) {
                            ThemeMode.LIGHT -> false
                            ThemeMode.DARK -> true
                            ThemeMode.SYSTEM -> view.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                        }
                        val defaultLightStatusBars = !defaultSystemDark

                        insetsController.isAppearanceLightStatusBars = if (isIconInverted) {
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
    val sharedPref = remember { context.getSharedPreferences("booknote_prefs", Context.MODE_PRIVATE) }
    var showDate by remember { mutableStateOf(sharedPref.getBoolean("show_date", true)) }

    val noteViewModel: NoteViewModel = viewModel()
    val notes by noteViewModel.notesState.collectAsState()

    // 🌟 全局照相机与动态悬浮插槽
    val hazeState = remember { HazeState() }
    val floatingUIState = remember { mutableStateOf<(@Composable BoxScope.() -> Unit)?>(null) }

    val themeManager = remember { ThemeSettingsManager(context) }
    val themeState by themeManager.themeStateFlow.collectAsState(initial = AppThemeState())
    val dynamicBlur = if (currentRoute?.startsWith("edit/") == true) 15.dp else 0.dp

    // 🌟 广播通道，让所有子页面都能拿到相机和插槽
    CompositionLocalProvider(
        LocalGlobalHazeState provides hazeState,
        LocalFloatingUI provides floatingUIState
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ==========================================================
            // 第 1 层：全局背景源 (挂载 hazeSource，提供背景给上层)
            // ==========================================================
            Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
                AppBackgroundContainer(themeState = themeState, blurRadius = dynamicBlur) {
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.fillMaxSize(),
                        // === 转场动画保持不变 ===
                        enterTransition = {
                            val initialRoute = initialState.destination.route?.lowercase() ?: ""
                            val targetRoute = targetState.destination.route?.lowercase() ?: ""
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
                        composable("home") {
                            val actContext = LocalContext.current
                            BackHandler {
                                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                                    addCategory(android.content.Intent.CATEGORY_HOME)
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                actContext.startActivity(intent)
                            }
                            HomeScreen(notes, showDate, navController, noteViewModel)
                        }

                        composable("settings") {
                            SettingsScreen(notes, showDate, navController, noteViewModel) { showDate = it }
                        }

                        composable("archive") {
                            // 🌟 删除了 hazeState 传参
                            ArchiveScreen(notes, showDate, navController, noteViewModel, themeState)
                        }

                        composable("trash") {
                            // 🌟 删除了 hazeState 传参
                            TrashScreen(notes, showDate, navController, noteViewModel, themeState)
                        }

                        composable("todo_screen") {
                            // 🌟 删除了 hazeState 传参
                            TodoScreen(navController, themeState)
                        }

                        composable("more_settings_screen") {
                            // 🌟 极致瘦身：只需传 navController
                            MoreSettingsScreen(navController = navController)
                        }

                        composable("edit/{noteId}") { backStackEntry ->
                            val noteId = backStackEntry.arguments?.getString("noteId") ?: ""
                            EditNoteScreen(navController, noteId, notes, noteViewModel)
                        }
                    }
                }
            }

            // 第 2 层：全局主导航栏 (保持不变)
            if (currentRoute == "home" || currentRoute == "settings") {
                if (themeState.isLiquidNavEnabled) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        BookNoteTheme {
                            LiquidGlassNavigationBar(
                                items = listOf(
                                    BottomNavItem("home", "首页", Icons.Filled.Home, Icons.Outlined.Home),
                                    BottomNavItem("settings", "设置", Icons.Filled.Settings, Icons.Outlined.Settings)
                                ),
                                currentRoute = currentRoute,
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onAddClick = { navController.navigate("edit/new") },
                                hazeState = hazeState
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        BookNoteTheme {
                            FloatingBottomBar(navController = navController, currentRoute = currentRoute)
                        }
                    }
                }
            }
            // 🌟 第 3 层：各页面的动态插槽投射 (绝对平级，毛玻璃 100% 生效！)
            floatingUIState.value?.invoke(this)
        }
    }
}