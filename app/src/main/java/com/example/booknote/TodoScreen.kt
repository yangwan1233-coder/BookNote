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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.booknote.ui.theme.BookNoteTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    navController: NavHostController,
    themeState: AppThemeState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val todos = remember { mutableStateListOf<TodoEntity>() }
    val scrollState = rememberScrollState()
    val dao = remember { AppDatabase.getInstance(context).todoDao() }

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

    val currentNavController by rememberUpdatedState(navController)
    val currentThemeState by rememberUpdatedState(themeState)

    LaunchedEffect(Unit) {
        val loadedTodos = withContext(Dispatchers.IO) { dao.getAllTodos() }
        todos.clear()
        todos.addAll(loadedTodos)
    }

    var currentRoute by remember { mutableStateOf("dummy") }

    val pendingTodos by remember { derivedStateOf { todos.filter { !it.isCompleted } } }
    val completedTodos by remember { derivedStateOf { todos.filter { it.isCompleted }.sortedByDescending { it.completedAt ?: 0L } } }

    var expandPending by remember { mutableStateOf(true) }
    var expandCompleted by remember { mutableStateOf(false) }

    var showDialog by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var editingItem by remember { mutableStateOf<TodoEntity?>(null) }

    val activity = context as? ComponentActivity
    LaunchedEffect(activity?.intent) {
        val target = activity?.intent?.getStringExtra("shortcut_target")
        if (target == "new_todo_screen") {
            editingItem = null
            inputText = ""
            showDialog = true
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

    // 🌟 动态插槽投射：将按钮置于全局顶层渲染
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
                                .align(Alignment.BottomCenter)
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
                                    baseWidth = 80.dp, baseHeight = 56.dp,
                                    onClick = { currentNavController.popBackStack() },
                                    icon = Icons.Default.ArrowBack,
                                    isIndicatorStyle = true,
                                    hazeState = globalHazeState // 完美获取全局状态
                                )


                                Spacer(modifier = Modifier.width(20.dp))

                                LiquidGlassSingleButton(
                                    baseWidth = 80.dp, baseHeight = 56.dp,
                                    onClick = {
                                        editingItem = null
                                        inputText = ""
                                        showDialog = true
                                    },
                                    icon = Icons.Default.Add,
                                    isIndicatorStyle = true,
                                    hazeState = globalHazeState
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(
                                    bottom = WindowInsets.navigationBars.asPaddingValues()
                                        .calculateBottomPadding() + 24.dp
                                ),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.width(100.dp).height(56.dp)
                                    .shadow(elevation = 8.dp, shape = CircleShape).clip(CircleShape)
                                    .clickable { currentNavController.popBackStack() }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        Icons.Default.ArrowBack,
                                        contentDescription = "返回",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.width(130.dp).height(56.dp)
                                    .shadow(elevation = 8.dp, shape = CircleShape).clip(CircleShape)
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
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "新建",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "新建",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 8.dp
            ) {
                Text(
                    text = "待办事项", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedTodoContainer(
                title = "📌 待办任务",
                count = pendingTodos.size,
                isExpanded = expandPending,
                onToggleExpand = { expandPending = !expandPending },
                items = pendingTodos,
                onItemStateChange = { item ->
                    val index = todos.indexOfFirst { it.id == item.id }
                    if (index != -1) {
                        val updated = todos[index].copy(isCompleted = true, completedAt = System.currentTimeMillis(), timestamp = System.currentTimeMillis())
                        todos[index] = updated
                        val currentCompleted = todos.filter { it.isCompleted }.sortedByDescending { it.completedAt ?: 0L }
                        val toDeleteFromDb = mutableListOf<TodoEntity>()
                        if (currentCompleted.size > 5) {
                            val itemsToRemove = currentCompleted.drop(5)
                            toDeleteFromDb.addAll(itemsToRemove)
                            val idsToRemove = itemsToRemove.map { it.id }.toSet()
                            todos.removeAll { it.id in idsToRemove }
                        }
                        scope.launch(Dispatchers.IO) {
                            toDeleteFromDb.forEach { dao.deleteTodo(it) }
                            dao.insertTodo(updated)
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

            AnimatedTodoContainer(
                title = "✅ 已完成 (限存5条)",
                count = completedTodos.size,
                isExpanded = expandCompleted,
                onToggleExpand = { expandCompleted = !expandCompleted },
                items = completedTodos,
                onItemStateChange = { item ->
                    val index = todos.indexOfFirst { it.id == item.id }
                    if (index != -1) {
                        val updated = todos[index].copy(isCompleted = false, completedAt = null, timestamp = System.currentTimeMillis())
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

            Spacer(modifier = Modifier.height(140.dp))
        }

        if (showDialog) {
            val focusRequester = remember { FocusRequester() }
            val keyboardController = LocalSoftwareKeyboardController.current

            LaunchedEffect(Unit) {
                delay(200)
                try {
                    focusRequester.requestFocus()
                    keyboardController?.show()
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
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        shape = RoundedCornerShape(16.dp)
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (inputText.isNotBlank()) {
                            if (editingItem != null) {
                                val index = todos.indexOfFirst { it.id == editingItem!!.id }
                                if (index != -1) {
                                    val updated = todos[index].copy(text = inputText, timestamp = System.currentTimeMillis())
                                    todos[index] = updated
                                    scope.launch(Dispatchers.IO) {
                                        dao.insertTodo(updated)
                                        WidgetUpdater.forceUpdate(context)
                                    }
                                }
                            } else {
                                val newTodo = TodoEntity(id = java.util.UUID.randomUUID().toString(), text = inputText, isCompleted = false, completedAt = null, timestamp = System.currentTimeMillis())
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