package com.example.booknote

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.booknote.ui.theme.BookNoteTheme
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
@Composable
fun MoreSettingsScreen(navController: NavHostController) { // 🌟 不再接收任何外部 HazeState
    val context = LocalContext.current
    val themeManager = remember { ThemeSettingsManager(context) }
    val themeState by themeManager.themeStateFlow.collectAsState(initial = AppThemeState())
    var showPermissionDialog by remember { mutableStateOf(false) }

    // 🌟 1. 接入全局通信管道：获取全局相机与空插槽
    val floatingUI = LocalFloatingUI.current
    val globalHazeState = LocalGlobalHazeState.current

    // 【新增】1. 获取界面的生命周期并定义一个动画开关
    val lifecycleOwner = LocalLifecycleOwner.current
    var isOverlayVisible by remember { mutableStateOf(false) }

// 【新增】2. 添加生命周期监听器
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                isOverlayVisible = true  // 页面刚展现时，打开开关
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                isOverlayVisible = false // 触发返回、页面准备退出时，立刻关闭开关
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 捕获最新状态，供 DisposableEffect 内部使用
    val currentNavController by rememberUpdatedState(navController)
    val currentThemeState by rememberUpdatedState(themeState)

    // 🌟 2. 动态投射悬浮层：当页面进入时写入插槽，离开时自动清空！
    DisposableEffect(Unit) {
        floatingUI.value = {
            androidx.compose.animation.AnimatedVisibility(
                visible = isOverlayVisible, // 绑定刚刚新增的开关状态
                enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
                exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200)),
                modifier = Modifier.matchParentSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (currentThemeState.isLiquidNavEnabled) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter) // 享有 BoxScope，可以直接对齐到底部
                                .fillMaxWidth()
                                .padding(
                                    bottom = WindowInsets.navigationBars.asPaddingValues()
                                        .calculateBottomPadding() + 12.dp
                                ),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BookNoteTheme {
                                LiquidGlassSingleButton(
                                    baseWidth = 80.dp,
                                    baseHeight = 56.dp,
                                    onClick = { currentNavController.popBackStack() },
                                    icon = Icons.Default.ArrowBack,
                                    isIndicatorStyle = true,
                                    hazeState = globalHazeState
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(
                                    bottom = WindowInsets.navigationBars.asPaddingValues()
                                        .calculateBottomPadding() + 24.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(56.dp)
                                    .clip(CircleShape)
                                    .clickable { currentNavController.popBackStack() }
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "返回",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        onDispose { floatingUI.value = null }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 🌟 此处的 Box 不再需要 .haze(localHazeState)，因为它本身就在 MainActivity 的 HazeSource 中！
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.statusBarsPadding().height(16.dp))

                // ================= 1. 顶部标题 =================
                Surface(
                    shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 8.dp
                ) {
                    Text(
                        text = "更多设置", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // ================= 2. 桌面待办部件 折叠卡片 =================
                var isWidgetPreviewExpanded by remember { mutableStateOf(false) }
                val widgetArrowRotation by animateFloatAsState(
                    targetValue = if (isWidgetPreviewExpanded) 180f else 0f, label = "WidgetArrowRotation"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isWidgetPreviewExpanded = !isWidgetPreviewExpanded }
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Spacer(modifier = Modifier.size(24.dp))
                            Text(
                                text = "🧩 添加桌面小部件", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ExpandMore, contentDescription = "展开或收起",
                                modifier = Modifier.size(24.dp).rotate(widgetArrowRotation),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }

                        AnimatedVisibility(
                            visible = isWidgetPreviewExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Divider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 1.dp,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                Text(
                                    text = "点击下方部件将其添加至桌面", fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    val scaleRatio = 0.8f
                                    Surface(
                                        modifier = Modifier
                                            .graphicsLayer { scaleX = scaleRatio; scaleY = scaleRatio }
                                            .requiredSize(430.dp)
                                            .clip(RoundedCornerShape(73.dp))
                                            .clickable {
                                                val needManualPermission = requestPinTodoShortcut(context)
                                                if (needManualPermission) showPermissionDialog = true
                                            },
                                        color = MaterialTheme.colorScheme.secondaryContainer, shadowElevation = 16.dp
                                    ) {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().height(73.dp).background(Color(0xFFFFD54F)),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Spacer(modifier = Modifier.width(73.dp))
                                                Text(
                                                    text = "待办事项", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                                                    color = Color.Black, modifier = Modifier.weight(1f), textAlign = TextAlign.Left
                                                )
                                                Box(modifier = Modifier.size(73.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                                                    Icon(imageVector = Icons.Default.Add, contentDescription = "新建", tint = Color.Black, modifier = Modifier.size(36.dp))
                                                }
                                            }
                                            Column(
                                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 32.dp, vertical = 24.dp),
                                                verticalArrangement = Arrangement.SpaceEvenly
                                            ) {
                                                MockTodoItem("1. 提醒：请先设置权限")
                                                MockTodoItem("2. 允许添加桌面快捷方式")
                                                MockTodoItem("3. 然后完成两条待办测试")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ================= 3. 全局主题与背景设置模块 =================
                MoreSettingsThemeOptions(themeState = themeState, themeManager = themeManager)
                Spacer(modifier = Modifier.navigationBarsPadding().height(80.dp))
            }
        }
    } // 结束外层根 Box

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要开启桌面小部件权限", fontWeight = FontWeight.Bold) },
            text = { Text("由于系统安全拦截，请在接下来的设置界面中，手动将“创建桌面快捷方式”或“添加桌面小部件”权限勾选为“允许”。", fontSize = 15.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法自动跳转，请去手机系统设置中手动开启", Toast.LENGTH_LONG).show()
                        }
                    }
                ) { Text("去开启权限") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
fun MockTodoItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.background).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun requestPinTodoShortcut(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val myProvider = ComponentName(context, TodoWidgetReceiver::class.java)

        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            return try {
                val isRequested = appWidgetManager.requestPinAppWidget(myProvider, null, null)
                if (isRequested) {
                    Toast.makeText(context, "已发送请求，请留意系统弹窗...", Toast.LENGTH_SHORT).show()
                    false
                } else {
                    true
                }
            } catch (e: Exception) {
                true
            }
        } else {
            Toast.makeText(context, "您的手机桌面Launcher已彻底禁用了小部件添加", Toast.LENGTH_LONG).show()
            return false
        }
    } else {
        Toast.makeText(context, "当前安卓版本过低，请长按桌面手动添加小部件", Toast.LENGTH_LONG).show()
        return false
    }
}