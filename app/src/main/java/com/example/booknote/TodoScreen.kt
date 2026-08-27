package com.example.booknote

import androidx.compose.ui.platform.LocalSoftwareKeyboardController // 别忘了导包
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.activity.ComponentActivity
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode.Companion.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.BackgroundModifier
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import androidx.compose.ui.graphics.Color
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    navController: NavHostController,
    themeState: AppThemeState //
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 【Room 架构升级】：直接使用 TodoEntity，废弃旧的 TodoItem
    val todos = remember { mutableStateListOf<TodoEntity>() }
    val scrollState = rememberScrollState()

    // 获取 Room 数据库的操作接口 (DAO)
    val dao = remember { AppDatabase.getInstance(context).todoDao() }

    // 【极速加载】：异步从 Room 数据库中一次性加载全量数据
    LaunchedEffect(Unit) {
        val loadedTodos = withContext(Dispatchers.IO) { dao.getAllTodos() }
        todos.clear()
        todos.addAll(loadedTodos)
    }

    // 1. 初始化模糊引擎
    val hazeState = remember { dev.chrisbanes.haze.HazeState() }

// 2. 初始化防抖路由状态（必须写一个非 "back" 的初始值，否则第一次点击返回会无效）
    var currentRoute by remember { mutableStateOf("dummy") }

// 3. 定义玻璃导航栏需要的返回图标数据
    val todoNavItems = remember {
        listOf(
            BottomNavItem(
                route = "back",
                title = "返回",
                activeIcon = Icons.Default.ArrowBack,
                inactiveIcon = Icons.Default.ArrowBack
            )
        )
    }

    // 派生状态引擎 (自动监听 todos 变化)
    val pendingTodos by remember { derivedStateOf { todos.filter { !it.isCompleted } } }
    val completedTodos by remember { derivedStateOf { todos.filter { it.isCompleted }.sortedByDescending { it.completedAt ?: 0L } } }

    var expandPending by remember { mutableStateOf(true) }
    var expandCompleted by remember { mutableStateOf(false) }

    var showDialog by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var editingItem by remember { mutableStateOf<TodoEntity?>(null) }

    // 【深度链接处理】：监听来自 Intent 的指令
    val activity = context as? ComponentActivity
    LaunchedEffect(activity?.intent) {
        val target = activity?.intent?.getStringExtra("shortcut_target")
        if (target == "new_todo_screen") {
            editingItem = null
            inputText = ""
            showDialog = true
            // 消费掉意图，防止旋转屏幕等操作重复触发
            activity?.intent?.removeExtra("shortcut_target")
        } else if (target == "edit_todo_screen") {
            val todoId = activity?.intent?.getStringExtra("clicked_todo_id")
            if (todoId != null) {
                val item = withContext(Dispatchers.IO) { dao.getAllTodos().find { it.id == todoId } }
                if (item != null) {
                    editingItem = item
                    inputText = item.text
                    showDialog = true
                }
            }
            activity?.intent?.removeExtra("shortcut_target")
            activity?.intent?.removeExtra("clicked_todo_id")
        }
    }

    Box(modifier = Modifier.fillMaxSize()
        //.background(MaterialTheme.colorScheme.background)

    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .haze(hazeState) // ✅ haze 必须挂在这个内容 Box 上
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(modifier = Modifier.height(16.dp))

                // 顶部标题
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 8.dp
                ) {
                    Text(
                        text = "待办事项",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ================= 待办容器 =================
                AnimatedTodoContainer(
                    title = "📌 待办任务",
                    count = pendingTodos.size,
                    isExpanded = expandPending,
                    onToggleExpand = { expandPending = !expandPending },
                    items = pendingTodos,
                    onItemStateChange = { item ->
                        val index = todos.indexOfFirst { it.id == item.id }
                        if (index != -1) {
                            // 1. UI 线程立刻更新状态，保证动画丝滑无延迟
                            val updated = todos[index].copy(
                                isCompleted = true,
                                completedAt = System.currentTimeMillis(),
                                timestamp = System.currentTimeMillis() // 更新时间戳以确保排序
                            )
                            todos[index] = updated

                            // 2. 高效截断算法：始终只保留最新的 5 条已完成
                            val currentCompleted = todos.filter { it.isCompleted }
                                .sortedByDescending { it.completedAt ?: 0L }
                            val toDeleteFromDb = mutableListOf<TodoEntity>()
                            if (currentCompleted.size > 5) {
                                val itemsToRemove = currentCompleted.drop(5)
                                toDeleteFromDb.addAll(itemsToRemove)
                                val idsToRemove = itemsToRemove.map { it.id }.toSet()
                                todos.removeAll { it.id in idsToRemove }
                            }

                            // 3. 【核心同步】：在后台线程安全写入 Room，并通知桌面刷新！
                            scope.launch(Dispatchers.IO) {
                                // 清理多余数据
                                toDeleteFromDb.forEach { dao.deleteTodo(it) }
                                // 存储最新状态
                                dao.insertTodo(updated)
                                // 呼叫桌面小部件瞬间重绘
                                WidgetUpdater.forceUpdate(context)
                            }
                        }
                    },
                    onItemClick = { item ->
                        editingItem = item
                        inputText = item.text
                        showDialog = true
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ================= 已完成容器 =================
                AnimatedTodoContainer(
                    title = "✅ 已完成 (限存5条)",
                    count = completedTodos.size,
                    isExpanded = expandCompleted,
                    onToggleExpand = { expandCompleted = !expandCompleted },
                    items = completedTodos,
                    onItemStateChange = { item ->
                        val index = todos.indexOfFirst { it.id == item.id }
                        if (index != -1) {
                            val updated = todos[index].copy(
                                isCompleted = false,
                                completedAt = null,
                                timestamp = System.currentTimeMillis()
                            )
                            todos[index] = updated

                            scope.launch(Dispatchers.IO) {
                                dao.insertTodo(updated)
                                WidgetUpdater.forceUpdate(context)
                            }
                            expandPending = true
                        }
                    },
                    onItemClick = {}
                )

                Spacer(modifier = Modifier.height(140.dp)) // 底部防遮挡留白
            }
        }

            // ================= 底部双按钮 =================

        // ================= 底部双按钮 =================

        // 🌟 核心修复：加上主题开关判断，恢复手动切换功能！
        if (themeState.isLiquidNavEnabled) {
            // 💎 状态 A：液态玻璃双按钮
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding() + 12.dp
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：纯图标返回按钮
                LiquidGlassSingleButton(
                    baseWidth = 100.dp,
                    baseHeight = 48.dp,
                    onClick = { navController.popBackStack() },
                    icon = Icons.Default.ArrowBack,
                    hazeState = hazeState
                )

                Spacer(modifier = Modifier.width(20.dp))

                // 右侧：纯图标新建按钮 (带底色指示块)
                LiquidGlassSingleButton(
                    baseWidth = 100.dp,
                    baseHeight = 56.dp,
                    onClick = {
                        editingItem = null
                        inputText = ""
                        showDialog = true
                    },
                    icon = Icons.Default.Add,
                    isIndicatorStyle = true,
                    hazeState = hazeState
                )
            }
        } else {
            // 🧱 状态 B：关闭开关，退回原版实心双按钮样式
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        // 保持和玻璃版本一样的高度，切换时才不会有跳跃感
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 返回按钮 (原版)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .width(100.dp)
                        .height(56.dp)
                        .shadow(elevation = 8.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .clickable { navController.popBackStack() }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                // 新建按钮 (原版，带文字)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .width(130.dp) // 原版带文字，宽度为 130.dp
                        .height(56.dp)
                        .shadow(elevation = 8.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .clickable {
                            editingItem = null
                            inputText = ""
                            showDialog = true
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "新建", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("新建", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }


    }
    // ================= 编辑/新建弹窗 =================
            if (showDialog) {
                // 1. 焦点请求器
                val focusRequester = remember { FocusRequester() }
                // 2. 【核心新增】：强制键盘控制器
                val keyboardController = LocalSoftwareKeyboardController.current

                // 3. 终极双保险触发机制
                LaunchedEffect(Unit) {
                    // 延迟 200ms 等待系统弹窗彻底绘制完毕并拿到 Window Token
                    delay(200)
                    try {
                        focusRequester.requestFocus() // 第一步：让输入框拿到光标
                        keyboardController?.show()    // 第二步：拿着大喇叭强制系统呼出键盘！
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text(text = if (editingItem != null) "编辑事项" else "新建待办", fontWeight = FontWeight.Bold) },
                    text = {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("请输入任务内容...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester), // 绑定焦点
                            shape = RoundedCornerShape(16.dp)
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (inputText.isNotBlank()) {
                                if (editingItem != null) {
                                    val index = todos.indexOfFirst { it.id == editingItem!!.id }
                                    if (index != -1) {
                                        val updated = todos[index].copy(
                                            text = inputText,
                                            timestamp = System.currentTimeMillis()
                                        )
                                        todos[index] = updated
                                        scope.launch(Dispatchers.IO) {
                                            dao.insertTodo(updated)
                                            WidgetUpdater.forceUpdate(context)
                                        }
                                    }
                                } else {
                                    val newTodo = TodoEntity(
                                        id = java.util.UUID.randomUUID().toString(),
                                        text = inputText,
                                        isCompleted = false,
                                        completedAt = null,
                                        timestamp = System.currentTimeMillis()
                                    )
                                    todos.add(0, newTodo)
                                    scope.launch(Dispatchers.IO) {
                                        dao.insertTodo(newTodo)
                                        WidgetUpdater.forceUpdate(context)
                                    }
                                    expandPending = true
                                }
                            }
                            showDialog = false
                        }) { Text("保存") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDialog = false }) { Text("取消") }
                    }
                )
            }
}

// 容器动画组件
@Composable
fun AnimatedTodoContainer(
    title: String,
    count: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    items: List<TodoEntity>,
    onItemStateChange: (TodoEntity) -> Unit,
    onItemClick: (TodoEntity) -> Unit
) {
    AnimatedContent(
        targetState = isExpanded,
        label = "ContainerTransform",
        transitionSpec = {
            (fadeIn(tween(300)) + scaleIn(initialScale = 0.8f, animationSpec = tween(300, easing = FastOutSlowInEasing)))
                .togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 0.8f, animationSpec = tween(200)))
                .using(SizeTransform(clip = false))
        }
    ) { expanded ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onToggleExpand() }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$title ($count)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (expanded) "收起" else "展开",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (expanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (items.isEmpty()) {
                        Text(
                            "这里空空如也 ~",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items.forEach { todo ->
                                key(todo.id) {
                                    AnimatableTodoItemRow(
                                        todo = todo,
                                        onStateChange = { onItemStateChange(todo) },
                                        onClick = { onItemClick(todo) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 彻底修复的动画列表项组件
@Composable
fun AnimatableTodoItemRow(
    todo: TodoEntity,
    onStateChange: () -> Unit,
    onClick: () -> Unit
) {
    var isVisible by remember(todo.isCompleted) { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(300)) + slideInHorizontally(tween(300)) { -it / 3 },
        exit = slideOutHorizontally(tween(400)) { it } + fadeOut(tween(300))
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable { onClick() },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        isVisible = false
                        scope.launch {
                            delay(400) // 等待动画飞出
                            onStateChange() // 回调进行数据层转移
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (todo.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "切换状态",
                        tint = if (todo.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = todo.text,
                    fontSize = 16.sp,
                    color = if (todo.isCompleted) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSecondaryContainer,
                    textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.weight(1f)
                )

                if (todo.isCompleted && todo.completedAt != null) {
                    val timeStr = remember(todo.completedAt) {
                        try {
                            val sdf = SimpleDateFormat("M/d HH:mm", Locale.getDefault())
                            sdf.format(Date(todo.completedAt))
                        } catch (e: Exception) {
                            ""
                        }
                    }
                    Text(
                        text = timeStr,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}