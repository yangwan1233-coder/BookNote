package com.example.booknote

import androidx.compose.ui.focus.onFocusChanged
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.IntrinsicSize
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import dev.shreyaspatil.capturable.controller.rememberCaptureController
import androidx.compose.ui.graphics.asImageBitmap
import dev.chrisbanes.haze.haze
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.booknote.ui.theme.BookNoteTheme

// ================== 第二部分：主页代码 ==================
@Composable
fun HomeScreen(notes: List<Note>, showDate: Boolean, navController: NavHostController, noteViewModel: NoteViewModel) {
    // ... 前面的代码保持不变 ...
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // 安全的搜索状态
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 🌟 【核心修复】：去掉坑人的 remember(notes)！
    // 恢复每次重组时直接计算，彻底解决冷启动白屏、新增笔记不显示的问题！
    val displayNotes = notes.filter { note ->
        !note.isArchived && !note.isDeleted &&
                (searchQuery.isEmpty() ||
                        note.title.contains(searchQuery, ignoreCase = true) ||
                        note.content.contains(searchQuery, ignoreCase = true))
    }.sortedBy { it.createdAt }

    val listState = rememberLazyListState()

    // 🌟 新增：在列表级别管理当前被侧滑的卡片 ID，安全且遵循单向数据流
    var currentlySwipedNoteId by remember { mutableStateOf<String?>(null) }

    // 只有在搜索框展开时，才启用拦截器
    BackHandler(enabled = isSearchExpanded) {
        isSearchExpanded = false
        searchQuery = ""
    }

    // 【您的原版逻辑完全保留】：自动滚动到底部绝对未动
    LaunchedEffect(displayNotes.size) { if (displayNotes.isNotEmpty()) listState.animateScrollToItem(displayNotes.size) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (notes.isEmpty()) {
            // 正在加载或者确实没有笔记时的友好提示
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有笔记，点击右下角开始记录吧 ✨", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        } else {
            LazyColumn(
                state = listState, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 120.dp,
                    bottom = 140.dp
                )
                // ❌ 第一步：彻底删掉那句霸道的 verticalArrangement = Arrangement.spacedBy(...)
            ) {
                // 安全的按月分组逻辑，保证 Compose 列表刷新时不会闪退
                val groupedNotes = displayNotes.groupBy {
                    val y = formatTime(it.createdAt, "yyyy")
                    val m = formatTime(it.createdAt, "M")
                    "$y-$m"
                }

                var lastYear = ""
                groupedNotes.forEach { (monthKey, monthNotes) ->
                    val year = monthKey.split("-")[0]
                    val month = monthKey.split("-")[1]

                    val headerText = if (year != lastYear) {
                        lastYear = year
                        "${year}年 ${month}月"
                    } else {
                        "${month}月"
                    }

                    // ✅ 第二步：用普通的 padding 精准控制头部的上下间距
                    item(key = "header_$monthKey") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                // 重点在这里：top 控制距离上一张卡片的距离，bottom 控制距离下一张卡片的距离
                                // 您可以随意把 bottom 改成 0.dp 或者 2.dp，让它死死贴住下面的笔记！
                                .padding(top = 0.dp, bottom = 12.dp)
                                // 🌟 动画注入 1：让日期标题也拥有丝滑的出入场视差动画
                                .silkyScrollAnimation(listState = listState, itemKey = "header_$monthKey")
                        ) {
                            YearHeader(year = headerText)
                        }
                    }

                    // ✅ 第三处：把 items 升级为 itemsIndexed，实现“智能探测间距”
                    itemsIndexed(
                        items = monthNotes,
                        // 【核心修复】：加上 "note_" 前缀，既解决了类型推断报错，又保证了全局唯一性！
                        key = { _, note -> "note_${note.id}" }
                    ) { index, note ->

                        // 【核心探测逻辑】：判断当前笔记是不是这个月的最后一条？
                        val isLastNoteInMonth = index == monthNotes.lastIndex

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = if (isLastNoteInMonth) 12.dp else 12.dp)
                                .silkyScrollAnimation(listState = listState, itemKey = "note_${note.id}")
                        ) {
                            SwipeHoverNoteCard(
                                note = note,
                                showDate = showDate,
                                // 🌟 新增：传入当前被滑开的 ID，以及通知父级修改状态的回调
                                currentlySwipedId = currentlySwipedNoteId,
                                onSwipeStateChange = { id -> currentlySwipedNoteId = id },
                                onClick = {
                                    keyboardController?.hide()
                                    navController.navigate("edit/${note.id}")
                                },
                                onArchive = {
                                    val updatedNote = note.copy(isArchived = true)
                                    noteViewModel.saveNote(updatedNote)
                                },
                                onDelete = {
                                    val updatedNote = note.copy(isDeleted = true)
                                    noteViewModel.saveNote(updatedNote)
                                }
                            )
                        }
                    }
                }
            }
        }

        FloatingSearchTopBar(
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            isSearchExpanded = isSearchExpanded,
            onSearchExpandedChange = { isSearchExpanded = it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp)
        )
    }
}
data class NoteSnapshot(
    val title: String,
    val blocks: List<UIBlock>
)

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class // 【修复报错】：解决调出键盘API报红的问题
)
@Composable
fun EditNoteScreen(navController: NavHostController, noteId: String, notes: List<Note>, noteViewModel: NoteViewModel) {
    // ... 顶部的状态声明等代码保持完全不变 ...

    val redoStack = remember { mutableStateListOf<NoteSnapshot>() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 在编辑页面函数顶部获取颜色

    val themeManager = remember { ThemeSettingsManager(context) }
    val themeState by themeManager.themeStateFlow.collectAsState(initial = AppThemeState())
    // 1. 判断当前是否处于深色模式
    val isDarkTheme = when (themeState.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }

// 2. 🌟 【核心修复】：直接把存储的数字转成 Color 对象，去和 Color.Black 比较！
    val parsedColor = Color(themeState.noteTextColorHex)
    val customTextColor = if (isDarkTheme && parsedColor == Color.Black) {
        Color.White // 深色模式下，如果是纯黑，就强制变纯白
    } else {
        parsedColor // 其他情况（选了红黄蓝等）保持用户选的颜色
    } // 👈 动态转为 Compose Color
// ==================================================================
// 💡 【大厂级核心修复】：使用 remember 锁死 currentNote 的生命周期锚点！
// 无论页面因为用户操作或键盘弹起刷新多少次，在当前屏幕生存期间，我们只认定第一帧生成的笔记。
// 彻底粉碎新建笔记时，由于 UUID 动态改变导致表格、导图、图片被瞬间清空的隐藏 Bug！
// ==================================================================
    val currentNote = remember(noteId) { notes.find { it.id == noteId } ?: Note() }

    var title by remember { mutableStateOf(currentNote.title) }
    var content by remember { mutableStateOf(TextFieldValue(currentNote.content)) }

// 【核心检查 2】：图片列表必须从 currentNote.imagePaths 拿初始数据！
    var imagePaths by remember(currentNote.id) {
        mutableStateOf(currentNote.imagePaths)
    }
    var fullScreenImageIndex by remember { mutableStateOf<Int?>(null) }
    var imageToDelete by remember { mutableStateOf<String?>(null) }
    var showDates by remember { mutableStateOf(true) }

    // 🌟 新增：控制顶部悬浮提示的状态
    var showTopToast by remember { mutableStateOf(false) }
    var topToastMessage by remember { mutableStateOf("") }
    // 🌟 新增：长图导出引擎控制中心
    val captureController = rememberCaptureController()
    var isExporting by remember { mutableStateOf(false) }

    // 自动隐藏逻辑：展示 2 秒后消失
    LaunchedEffect(showTopToast) {
        if (showTopToast) {
            kotlinx.coroutines.delay(2000)
            showTopToast = false
        }
    }

// ================= 新增：焦点与键盘控制器 =================
    val titleFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

// ==========================================
// 【大厂级块级状态】：统一管理文本、表格、导图序列
// ==========================================
// 【智能读取】：如果是老笔记，走旧逻辑；如果是新架构笔记，精准恢复图表顺序！
// ==========================================
// 【核心检查 1】：必须带有 content 兜底！
// 这样万一 blocksJson 是空的，老笔记的纯文本 (content) 也能显示出来
    val activeBlocks = remember(currentNote.id) {
        if (currentNote.blocksJson.isNotBlank()) {
            BlockSerializer.deserializeBlocks(currentNote.blocksJson)
        } else if (currentNote.content.isNotBlank()) {
            mutableStateListOf<UIBlock>(UITextBlock(androidx.compose.ui.text.input.TextFieldValue(currentNote.content)))
        } else {
            mutableStateListOf<UIBlock>(UITextBlock())
        }
    }

// 2. 使用 SnapshotStateList 初始化历史栈，完美兼容 activeBlocks 的所有图表与文字
    val undoStack = remember {
        mutableStateListOf(
            NoteSnapshot(
                title = title,
                // 利用 BlockSerializer 深拷贝当前区块，防止数据指针污染
                blocks = activeBlocks.map { block ->
                    when (block) {
                        is UITextBlock -> block.copy(content = block.content)
                        is UITableBlock -> block.copy(tableData = block.tableData.copy())
                        is UIMindMapBlock -> block.copy(rootNode = block.rootNode)
                    }
                }
            )
        )
    }

    // 【新增】：快照捕捉函数，统一管理撤销栈
    val captureSnapshot = {
        val snapshot = NoteSnapshot(
            title = title,
            blocks = activeBlocks.map { block ->
                when (block) {
                    is UITextBlock -> block.copy(content = block.content)
                    is UITableBlock -> block.copy(tableData = block.tableData.copy())
                    is UIMindMapBlock -> block.copy(rootNode = block.rootNode)
                }
            }
        )
        if (undoStack.isEmpty() || undoStack.last() != snapshot) {
            undoStack.add(snapshot)
            if (undoStack.size > 30) undoStack.removeAt(0)
            redoStack.clear()
        }
    }
    var focusedBlockIndex by remember { mutableStateOf(0) } // 追踪当前光标停留在哪一行
    var selectedMindMapNodeId by remember { mutableStateOf<String?>(null) }
    // 延迟 100 毫秒确保页面渲染完，然后自动调出键盘聚焦标题
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        titleFocusRequester.requestFocus()
        keyboardController?.show()
    }
    // =======================================================

    LaunchedEffect(Unit) { kotlinx.coroutines.delay(10000); showDates = false }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        if (uris.isNotEmpty()) {
            val newPaths = uris.take(9 - imagePaths.size).mapNotNull { copyUriToSystemStorage(context, it) }
            imagePaths = imagePaths + newPaths
        }
    }

    val onBack = {
        val newBlocksJson = BlockSerializer.serializeBlocks(activeBlocks)
        val pureTextPreview = activeBlocks
            .filterIsInstance<UITextBlock>()
            .joinToString("\n") { it.content.text }
            .trim()

        val hasRichBlocks = activeBlocks.any { it is UITableBlock || it is UIMindMapBlock }
        val isEmpty = title.isBlank() && pureTextPreview.isBlank() && imagePaths.isEmpty() && !hasRichBlocks

        if (isEmpty) {
            // 🌟 核心修复 A：如果是空白笔记，直接让 ViewModel 删掉它
            noteViewModel.removeNoteById(currentNote.id)
            topToastMessage = "空白文档已舍弃"
            showTopToast = true
        } else {
            val updatedNote = currentNote.copy(
                title = title,
                content = pureTextPreview,
                blocksJson = newBlocksJson,
                imagePaths = imagePaths,
                updatedAt = System.currentTimeMillis()
            )

            // 🌟 核心修复 B：直接交给 ViewModel 统筹保存和落盘，无需再手动开协程写磁盘！
            noteViewModel.saveNote(updatedNote)

            topToastMessage = "内容已保存"
            showTopToast = true
        }

        scope.launch {
            delay(500)
            navController.popBackStack()
        }
    }
    BackHandler { onBack() }

    // 🌟 修复：去掉最外层 Box 的 imePadding，把它变成纯净的满屏容器
    Box(modifier = Modifier.fillMaxSize()) {
        // 让内部的 Column 自己去处理键盘高度
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding() // 👈 绝杀！键盘弹起指令下发给这个容器
        ) {
        Column(
            modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {

            Spacer(
                modifier = Modifier.height(
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 80.dp
                )
            )

            if (imagePaths.isNotEmpty()) {
                ImageGrid(
                    paths = imagePaths,
                    columns = 3,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    onImageClick = { clickedUrl ->
                        val idx = imagePaths.indexOf(clickedUrl)
                        if (idx >= 0) fullScreenImageIndex = idx
                    },
                    onImageLongClick = { imageToDelete = it }
                )
            }

            // 【只修改了 textStyle 中的 color】：改用 customTextColor（即 Color(themeState.noteTextColorHex)）
            TextField(
                value = title,
                onValueChange = { if (it.length <= 100) title = it },

                placeholder = { Text("输入标题", fontSize = 28.sp, fontWeight = FontWeight.Bold) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = customTextColor // 🌟 关键修改：替换 MaterialTheme.colorScheme.onSurface 为自定义字体颜色
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocusRequester),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Next
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onNext = { contentFocusRequester.requestFocus() }
                )
            )

            // ==========================================
            // 【核心重构】：大厂级流式区块渲染器 (Block Editor)
            // ==========================================
            activeBlocks.forEachIndexed { index, block ->
                when (block) {
                    is UITextBlock -> {
                        TextField(
                            value = block.content,
                            onValueChange = { newText ->
                                // 1. 更新当前行的文字内容
                                activeBlocks[index] = block.copy(content = newText)

                                // 2. 完美保留您的撤销/恢复功能！
                                // 【修复】：调用统一的快照捕捉逻辑
                                captureSnapshot()
                            },
                            placeholder = {
                                if (index == 0) Text("开始记录...", fontSize = 16.sp)
                                else Text(
                                    "继续输入...",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = customTextColor // 🌟 关键修改：应用自定义笔记字体颜色
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                // 第一块文本（正文）默认撑开，后续的文本块仅仅包裹内容，让布局极其紧凑自然
                                .defaultMinSize(minHeight = if (activeBlocks.size == 1) 400.dp else 40.dp)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        // 【极其关键】：实时记住当前光标停留在哪一行！这样插入组件才知道往哪插
                                        focusedBlockIndex = index

                                        // 【完美修复隐藏 Bug】：只要点击任何正文开始打字，瞬间隐藏导图悬浮控制台
                                        selectedMindMapNodeId = null
                                    }
                                }
                        )
                    }

                    is UITableBlock -> {
                        InteractiveTableBlock(
                            tableData = block.tableData,
                            onUpdate = { updatedTable ->
                                activeBlocks[index] = block.copy(tableData = updatedTable)
                            },
                            onDelete = { activeBlocks.removeAt(index) }
                        )
                    }

                    is UIMindMapBlock -> {
                        InteractiveMindMapBlock(
                            title = block.title,
                            rootNode = block.rootNode,
                            selectedNodeId = selectedMindMapNodeId,
                            onTitleChange = { newTitle ->
                                // ✅ 满分操作：使用 copy 产生新对象替换，完美触发 Compose 重组与数据落盘！
                                activeBlocks[index] = block.copy(title = newTitle)
                            },
                            onNodeSelect = { clickedNodeId ->
                                selectedMindMapNodeId = clickedNodeId
                                focusedBlockIndex = index

                                // 💡 【大厂级体验优化 1】：当选中导图节点准备交互时，智能收起软键盘
                                // 彻底解决软键盘遮挡宽大思维导图画布的痛点！
                                keyboardController?.hide()
                            },
                            onUpdate = { updatedRoot ->
                                activeBlocks[index] = block.copy(rootNode = updatedRoot)
                            },
                            onDeleteMap = {
                                activeBlocks.removeAt(index)
                                selectedMindMapNodeId = null

                                // 💡 【大厂级防越界优化 2】：删除当前块后，让焦点安全回退到上一个块
                                // 避免因列表缩短而导致 focusedBlockIndex 越界引发的隐藏闪退 Bug！
                                focusedBlockIndex = (index - 1).coerceAtLeast(0)
                            }
                        )
                    }
                }
            }

            // 保留底部安全距离，防止键盘或悬浮栏遮挡最后一行字
            Spacer(modifier = Modifier.height(140.dp))
        }
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. 左侧：原封不动保留你的返回按钮
                FloatingActionButton(
                    onClick = { onBack() },
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                    modifier = Modifier.width(48.dp).height(48.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, "返回", modifier = Modifier.scale(1.3f))
                }

                // 2. 右侧：🌟 大厂级联排胶囊按钮 (分享长图 + 保存)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onPrimary, // 保证里面的图标颜色统一
                    shadowElevation = 8.dp,
                    modifier = Modifier.height(48.dp) // 与左侧返回按钮高度保持绝对一致
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp) // 给内部图标留出呼吸空间
                    ) {
                        // 🌟 按钮 A：分享/导出长图
                        IconButton(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    isExporting = true
                                    topToastMessage = "正在生成长图..." // 🌟 气泡提示
                                    showTopToast = true

                                    scope.launch {
                                        // 🌟 核心修复：必须给 Coil 引擎 1.2 秒的异步加载图片时间！
                                        // 如果你的图片很多，甚至可以改成 1500ms
                                        kotlinx.coroutines.delay(1200)
                                        captureController.capture()
                                    }
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "导出长图", modifier = Modifier.scale(1.1f))
                        }

                        // 🌟 极简美学：半透明竖形分割线
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight(0.4f) // 高度只有胶囊的 40%，极其精致
                                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f))
                        )

                        // 🌟 按钮 B：原本的保存逻辑
                        IconButton(
                            onClick = { onBack() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Check, "保存", modifier = Modifier.scale(1.3f))
                        }
                    }
                }
            }

        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (showDates) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "创建: ${formatTime(currentNote.createdAt, "yyyy-MM-dd HH:mm")}",
                        fontSize = 12.sp,
                        color = customTextColor.copy(alpha = 0.8f) // 🌟 关键修改：替换为自定义笔记字体颜色
                    )
                    Text(
                        text = "编辑: ${
                            formatTime(
                                System.currentTimeMillis(),
                                "yyyy-MM-dd HH:mm"
                            )
                        }",
                        fontSize = 12.sp,
                        color = customTextColor.copy(alpha = 0.8f) // 🌟 关键修改：替换为自定义笔记字体颜色
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 🌟 修改：减少底部冗余留白，贴近系统安全区
                    .padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding() + 8.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                // 🌟 修改：通过 modifier 直接锁死高度为 56.dp
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    shadowElevation = 8.dp,
                    modifier = Modifier.height(56.dp) // 👈 绝杀：锁定与液态导航栏一致的高度
                ) {
                    Row(
                        // 🌟 修改：去掉垂直方向的 padding，让 Row 内部组件自己居中
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxHeight(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), // 稍微缩小间距，避免超出屏幕
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        IconButton(onClick = {
                            val fullText = content.text
                            val start = content.selection.min
                            val end = content.selection.max

                            if (start < end) {
                                val selectedText = fullText.substring(start, end)
                                val bulletedSelection =
                                    selectedText.split("\n").joinToString("\n") { line ->
                                        if (line.isNotBlank() && !line.trim()
                                                .startsWith("•")
                                        ) "• $line" else line
                                    }
                                val newText = fullText.substring(
                                    0,
                                    start
                                ) + bulletedSelection + fullText.substring(end)
                                content = TextFieldValue(
                                    text = newText,
                                    selection = TextRange(start, start + bulletedSelection.length)
                                )
                                Toast.makeText(
                                    context,
                                    "已将选中段落转化为列表",
                                    Toast.LENGTH_SHORT
                                ).show()

                                // 【修复】：记录快照
                                captureSnapshot()

                            } else {
                                Toast.makeText(
                                    context,
                                    "请先长按滑动选中需要加列表符号的几行文字",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }) {
                            Icon(
                                Icons.Default.List,
                                "列表排列",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // ==========================================
                        // 【撤销按钮】：支持图表与富文本的时光倒流
                        // ==========================================
                        IconButton(onClick = {
                            if (undoStack.size > 1) {
                                // 1. 把当前的最新状态压入重做栈
                                val currentState = undoStack.last()
                                redoStack.add(currentState)

                                // 2. 弹出撤销栈的最后一个
                                undoStack.removeAt(undoStack.size - 1)

                                // 3. 回退到上一个状态
                                val previousState = undoStack.last()

                                // 【核心同步】：把数据强力同步回 activeBlocks 界面
                                activeBlocks.clear()
                                activeBlocks.addAll(previousState.blocks)
                                title = previousState.title

                                // 重置光标，防止越界崩塌
                                focusedBlockIndex = (activeBlocks.size - 1).coerceAtLeast(0)
                                selectedMindMapNodeId = null
                            } else {
                                Toast.makeText(context, "无法撤销了", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                Icons.Default.Undo,
                                "撤销上一步",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // ==========================================
                        // 【重做按钮】：时光向前
                        // ==========================================
                        IconButton(onClick = {
                            if (redoStack.isNotEmpty()) {
                                // 1. 取出重做栈顶的未来状态
                                val nextState = redoStack.removeAt(redoStack.size - 1)

                                // 2. 压回撤销栈
                                undoStack.add(nextState)

                                // 【核心同步】：恢复到未来状态
                                activeBlocks.clear()
                                activeBlocks.addAll(nextState.blocks)
                                title = nextState.title

                                focusedBlockIndex = (activeBlocks.size - 1).coerceAtLeast(0)
                                selectedMindMapNodeId = null
                            } else {
                                Toast.makeText(context, "无法重做了", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                Icons.Default.Redo,
                                "重做下一步",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        // ... 您原有的撤销、重做等按钮 ...

                        // =====================================
                        // 【无 Bug 完美替换】：插入功能的联动
                        // =====================================
                        EditorBottomBar(
                            onMindMapClick = {
                                // 【安全计算插入位置】：紧贴着当前光标所在的下一行插入
                                val insertIndex =
                                    (focusedBlockIndex + 1).coerceIn(0, activeBlocks.size)

                                val newMap = UIMindMapBlock()
                                val newText = UITextBlock()

                                activeBlocks.add(insertIndex, newMap)
                                activeBlocks.add(insertIndex + 1, newText)

                                focusedBlockIndex = insertIndex + 1
                                selectedMindMapNodeId = newMap.rootNode.id // 自动激活面板
                            },
                            onTableClick = {
                                // 【安全计算插入位置】：紧贴着当前光标所在的下一行插入
                                val insertIndex =
                                    (focusedBlockIndex + 1).coerceIn(0, activeBlocks.size)

                                val newTable = UITableBlock()
                                val newText = UITextBlock()

                                activeBlocks.add(insertIndex, newTable)
                                activeBlocks.add(insertIndex + 1, newText)

                                focusedBlockIndex = insertIndex + 1
                            }
                        )


                        IconButton(onClick = {
                            if (imagePaths.size < 9) photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                            else Toast.makeText(context, "最多9张", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                Icons.Default.Image,
                                "插入图片",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
        // =====================================
        // 【升级】：极简胶囊形态的导图图标工具栏
        // =====================================
        if (selectedMindMapNodeId != null) {
            val mapIndex = activeBlocks.indexOfFirst {
                it is UIMindMapBlock && findMindMapNode(
                    it.rootNode,
                    selectedMindMapNodeId!!
                ) != null
            }
            if (mapIndex != -1) {
                val mapBlock = activeBlocks[mapIndex] as UIMindMapBlock

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            bottom = WindowInsets.navigationBars.asPaddingValues()
                                .calculateBottomPadding() + 90.dp
                        )
                        // 【核心修复】：去掉固定宽度，使用 wrapContentWidth 让背景自动紧紧包裹住三个图标
                        .wrapContentWidth(),
                    // 【核心修复】：使用 CircleShape 达成极致的 100% 圆角（左右呈半圆形，也就是胶囊形状）
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 12.dp
                ) {
                    // 使用横向 Row 将三个图标并排摆放，去掉所有冗余的 Column 和输入框
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. 加细节分支 (子节点) - 使用“右下折角箭头”表示层级深入
                        IconButton(onClick = {
                            activeBlocks[mapIndex] = mapBlock.copy(
                                rootNode = addMindMapChild(
                                    mapBlock.rootNode,
                                    selectedMindMapNodeId!!
                                )
                            )
                        }) {
                            Icon(
                                imageVector = Icons.Default.SubdirectoryArrowRight,
                                contentDescription = "加细节",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // 2. 加并列分支 (兄弟节点) - 使用“加号”表示同级新增
                        IconButton(onClick = {
                            activeBlocks[mapIndex] = mapBlock.copy(
                                rootNode = addMindMapSibling(
                                    mapBlock.rootNode,
                                    selectedMindMapNodeId!!
                                )
                            )
                        }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "加并列",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // 3. 删节点 - 使用“垃圾桶”图标，并标红以示警告
                        IconButton(onClick = {
                            val updated =
                                deleteMindMapNode(mapBlock.rootNode, selectedMindMapNodeId!!)
                            // 如果返回 null，说明删的是中心主题（根节点），则直接把整个导图区块删掉
                            if (updated == null) activeBlocks.removeAt(mapIndex)
                            else activeBlocks[mapIndex] = mapBlock.copy(rootNode = updated)

                            selectedMindMapNodeId = null
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删节点",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
        // =================================================================
        // 🌟 召唤隐藏的长图渲染舱

        val exportContext = LocalContext.current
        val exportSharedPref = remember { exportContext.getSharedPreferences("booknote_prefs", Context.MODE_PRIVATE) }
        val savedPatternName = exportSharedPref.getString("export_bg_pattern", "默认纯色")

        // 🌟 将本地图片强行塞入 Compose 原生 Brush 里，完美避开截图库的加载 BUG
        val exportBrush = remember(savedPatternName) {
            when (savedPatternName) {
                "极光幻彩" -> Brush.linearGradient(listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121)))
                "深空星海" -> Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)))
                "落日余晖" -> Brush.horizontalGradient(listOf(Color(0xFFFA709A), Color(0xFFFEE140)))
                "森系清新" -> Brush.verticalGradient(listOf(Color(0xFF134E5E), Color(0xFF71B280)))
                "自定义图片" -> {
                    var customBrush: Brush? = null
                    try {
                        val file = java.io.File(exportContext.filesDir, "export_bg_image.jpg")
                        if (file.exists()) {
                            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                            if (bitmap != null) {
                                val imageBitmap = bitmap.asImageBitmap()
                                val shader = ImageShader(imageBitmap, TileMode.Clamp, TileMode.Clamp)
                                customBrush = ShaderBrush(shader)
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                    customBrush
                }
                else -> null // 默认纯色
            }
        }

        NoteExportCanvas(
            isExporting = isExporting,
            captureController = captureController,
            title = title,
            timestamp = currentNote.createdAt,
            imagePaths = imagePaths,
            activeBlocks = activeBlocks,
            themeState = themeState,          // 👈 保留一次即可
            customTextColor = customTextColor, // 👈 保留一次即可
            exportBackgroundBrush = exportBrush, // 🌟 传入你的背景预设
            onCaptureComplete = { bitmap ->
                isExporting = false
                if (bitmap != null) {
                    scope.launch {
                        val success = saveBitmapToGallery(context, bitmap, title)
                        topToastMessage = if (success) "🎉 长图已保存到相册" else "长图保存失败"
                        showTopToast = true
                    }
                } else {
                    topToastMessage = "长图生成失败"
                    showTopToast = true
                }
            }
        )
        // 🌟 新增：位于页面最顶层的液态悬浮气泡
        // =================================================================
        androidx.compose.animation.AnimatedVisibility(
            visible = showTopToast,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }) + androidx.compose.animation.fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                // 精准计算状态栏高度，加上呼吸距，完美悬浮在顶部
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp)
                .zIndex(100f) // 绝对置顶
        ) {
            Box(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(horizontal = 24.dp)
                    // 🌟 设置 28dp 大圆角与液态玻璃导航栏完全一致
                    .graphicsLayer {
                        shadowElevation = 12.dp.toPx()
                        shape = RoundedCornerShape(28.dp)
                        clip = true
                    }
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = topToastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (fullScreenImageIndex != null) {
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = fullScreenImageIndex!!,
            pageCount = { imagePaths.size }
        )

        Dialog(onDismissRequest = { fullScreenImageIndex = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f))) {

                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    AsyncImage(
                        model = Uri.parse(imagePaths[page]),
                        contentDescription = "查看大图",
                        modifier = Modifier.fillMaxSize().clickable { fullScreenImageIndex = null },
                        contentScale = ContentScale.Fit
                    )
                }

                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 24.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 8.dp
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${imagePaths.size}",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.clickable {
                            val currentImgUri = Uri.parse(imagePaths[pagerState.currentPage])
                            val editIntent = android.content.Intent(android.content.Intent.ACTION_EDIT).apply {
                                setDataAndType(currentImgUri, "image/*")
                                flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            try {
                                context.startActivity(android.content.Intent.createChooser(editIntent, "使用系统工具裁剪/编辑"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "您的手机似乎未内置支持裁剪的相册应用", Toast.LENGTH_SHORT).show()
                            }
                        }.padding(horizontal = 32.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = "裁剪图片", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("裁剪与编辑", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (imageToDelete != null) {
        AlertDialog(
            onDismissRequest = { imageToDelete = null }, title = { Text("删除图片") }, text = { Text("确定要从笔记中删除这张图片吗？") },
            confirmButton = { Button(onClick = { imagePaths = imagePaths.filter { it != imageToDelete }; imageToDelete = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { imageToDelete = null }) { Text("取消") } }
        )
    }
}

@Composable
fun NoteCardThumbnail(note: Note, showDate: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            // 【改动1】：去掉了 IntrinsicSize.Min，不再依赖文字高度，避免塌陷！
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showDate) {
                Text(text = formatTime(note.createdAt), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), modifier = Modifier.padding(end = 12.dp))
            }

            // ================== 终极防塌陷九宫格 ==================
            if (note.imagePaths.isNotEmpty()) {
                val images = note.imagePaths.take(9)
                val count = images.size
                val spacing = 2.dp

                Box(modifier = Modifier.padding(end = 12.dp)) {
                    Box(
                        // 【改动2】：直接写死 80.dp 的正方形！神挡杀神，佛挡杀佛，绝对不可能塌陷！
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        when (count) {
                            1 -> {
                                AsyncImage(model = Uri.parse(images[0]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            2 -> {
                                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                                    AsyncImage(model = Uri.parse(images[0]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                                    AsyncImage(model = Uri.parse(images[1]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                                }
                            }
                            3 -> {
                                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                                    AsyncImage(model = Uri.parse(images[0]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                                        AsyncImage(model = Uri.parse(images[1]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                                        AsyncImage(model = Uri.parse(images[2]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                                    }
                                }
                            }
                            4 -> {
                                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                                        AsyncImage(model = Uri.parse(images[0]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                                        AsyncImage(model = Uri.parse(images[1]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                                    }
                                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                                        AsyncImage(model = Uri.parse(images[2]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                                        AsyncImage(model = Uri.parse(images[3]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                                    }
                                }
                            }
                            else -> {
                                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                                    for (row in 0..2) {
                                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                                            for (col in 0..2) {
                                                val index = row * 3 + col
                                                if (index < count) {
                                                    AsyncImage(
                                                        model = Uri.parse(images[index]),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                                    )
                                                } else {
                                                    Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // ================== 修改结束 ==================

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(text = if (note.title.isNotBlank()) note.title else "无标题", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = if (note.content.isNotBlank()) note.content else "空内容", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
@Composable
fun ArchiveScreen(
    notes: List<Note>,
    showDate: Boolean,
    navController: NavHostController,
    noteViewModel: NoteViewModel,
    themeState: AppThemeState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val archiveNotes = notes.filter { it.isArchived && !it.isDeleted }.sortedBy { it.createdAt }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    var showTopToast by remember { mutableStateOf(false) }
    var topToastMessage by remember { mutableStateOf("") }

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

    // 捕获最新状态，供投射插槽安全使用
    val currentNavController by rememberUpdatedState(navController)
    val currentThemeState by rememberUpdatedState(themeState)
    val currentNotes by rememberUpdatedState(notes)
    val currentViewModel by rememberUpdatedState(noteViewModel)

    LaunchedEffect(showTopToast) {
        if (showTopToast) {
            kotlinx.coroutines.delay(2000)
            showTopToast = false
        }
    }

    // 🌟 2. 动态投射悬浮层：把按钮和气泡“远程发送”到 MainActivity 的顶层
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
                                        .calculateBottomPadding() + 8.dp
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
                                    hazeState = globalHazeState // 🌟 使用全局相机
                                )

                                Spacer(modifier = Modifier.width(20.dp))

                                LiquidGlassSingleButton(
                                    baseWidth = 80.dp, baseHeight = 56.dp,
                                    onClick = {
                                        if (selectedIds.isNotEmpty()) {
                                            val updatedNotes = currentNotes.map {
                                                if (it.id in selectedIds) it.copy(isArchived = false) else it
                                            }
                                            currentViewModel.updateNotesList(updatedNotes)
                                            selectedIds = emptySet()
                                            topToastMessage = "已取消存档"
                                            showTopToast = true
                                        } else {
                                            topToastMessage = "请先点击选择"
                                            showTopToast = true
                                        }
                                    },
                                    icon = Icons.Default.Refresh,
                                    isIndicatorStyle = true,
                                    badgeCount = selectedIds.size,
                                    hazeState = globalHazeState
                                )
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .wrapContentWidth()
                                .padding(
                                    bottom = WindowInsets.navigationBars.asPaddingValues()
                                        .calculateBottomPadding() + 24.dp
                                ),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { currentNavController.popBackStack() }) {
                                    Text(
                                        "返回",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Button(onClick = {
                                    if (selectedIds.isNotEmpty()) {
                                        val updatedNotes = currentNotes.map {
                                            if (it.id in selectedIds) it.copy(isArchived = false) else it
                                        }
                                        currentViewModel.updateNotesList(updatedNotes)
                                        selectedIds = emptySet()
                                        topToastMessage = "已取消存档"
                                        showTopToast = true
                                    } else {
                                        topToastMessage = "请先点击选择"
                                        showTopToast = true
                                    }
                                }) { Text(if (selectedIds.isEmpty()) "恢复" else "恢复选中 (${selectedIds.size})") }
                            }
                        }
                    }
                }
            }

            // 悬浮气泡也一并投射到顶层，防止被其他内容遮挡
            androidx.compose.animation.AnimatedVisibility(
                visible = showTopToast,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }) + androidx.compose.animation.fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp)
                    .zIndex(100f)
            ) {
                Box(
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(horizontal = 24.dp)
                        .graphicsLayer {
                            shadowElevation = 12.dp.toPx()
                            shape = RoundedCornerShape(28.dp)
                            clip = true
                        }
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .border(width = 1.dp, color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(28.dp))
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = topToastMessage, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        onDispose { floatingUI.value = null }
    }

    // 🌟 3. 主界面极简纯粹，不再挂载本地 HazeState，因为它已身处 MainActivity 的全局 hazeSource 中
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 120.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            var currentYear = ""
            archiveNotes.forEach { note ->
                val noteYear = formatTime(note.createdAt, "yyyy年")
                if (noteYear != currentYear) { currentYear = noteYear; item { YearHeader(year = noteYear) } }
                item(key = note.id) {
                    val isSelected = selectedIds.contains(note.id)
                    Box(modifier = Modifier.fillMaxWidth().clickable { selectedIds = if (isSelected) selectedIds - note.id else selectedIds + note.id }) {
                        NoteCardThumbnail(note = note, showDate = showDate, onClick = { selectedIds = if (isSelected) selectedIds - note.id else selectedIds + note.id })
                        if (isSelected) Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape))
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 8.dp
        ) {
            Text(
                text = "归档箱", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)
            )
        }
    }
}
@Composable
fun TrashScreen(
    notes: List<Note>,
    showDate: Boolean,
    navController: NavHostController,
    noteViewModel: NoteViewModel,
    themeState: AppThemeState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trashNotes = notes.filter { it.isDeleted }.sortedBy { it.createdAt }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    var showTopToast by remember { mutableStateOf(false) }
    var topToastMessage by remember { mutableStateOf("") }

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
    val currentNotes by rememberUpdatedState(notes)
    val currentViewModel by rememberUpdatedState(noteViewModel)

    LaunchedEffect(showTopToast) {
        if (showTopToast) {
            kotlinx.coroutines.delay(2000)
            showTopToast = false
        }
    }

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
                                        .calculateBottomPadding() + 8.dp
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
                                    hazeState = globalHazeState
                                )
                                Spacer(modifier = Modifier.width(16.dp))

                                LiquidGlassSingleButton(
                                    baseWidth = 80.dp, baseHeight = 56.dp,
                                    onClick = {
                                        if (selectedIds.isNotEmpty()) {
                                            val updatedNotes = currentNotes.map {
                                                if (it.id in selectedIds) it.copy(isDeleted = false) else it
                                            }
                                            currentViewModel.updateNotesList(updatedNotes)
                                            selectedIds = emptySet()
                                            topToastMessage = "笔记已成功恢复"
                                            showTopToast = true
                                        } else {
                                            topToastMessage = "请先点击选择"
                                            showTopToast = true
                                        }
                                    },
                                    icon = Icons.Default.Refresh,
                                    isIndicatorStyle = true,
                                    badgeCount = selectedIds.size,
                                    hazeState = globalHazeState
                                )
                                Spacer(modifier = Modifier.width(16.dp))

                                LiquidGlassSingleButton(
                                    baseWidth = 80.dp, baseHeight = 56.dp,
                                    onClick = {
                                        if (trashNotes.isEmpty()) {
                                            topToastMessage = "回收站已经是空的了"
                                            showTopToast = true
                                            return@LiquidGlassSingleButton
                                        }
                                        val updatedNotes = currentNotes.filterNot { it.isDeleted }
                                        currentViewModel.updateNotesList(updatedNotes)
                                        selectedIds = emptySet()
                                        topToastMessage = "已彻底清空"
                                        showTopToast = true
                                    },
                                    icon = Icons.Default.Delete,
                                    iconTint = MaterialTheme.colorScheme.error,
                                    isIndicatorStyle = true,
                                    hazeState = globalHazeState
                                )
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .wrapContentWidth()
                                .padding(
                                    bottom = WindowInsets.navigationBars.asPaddingValues()
                                        .calculateBottomPadding() + 24.dp
                                ),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { currentNavController.popBackStack() }) {
                                    Text(
                                        "返回",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (selectedIds.isNotEmpty()) {
                                            val updatedNotes = currentNotes.map {
                                                if (it.id in selectedIds) it.copy(isDeleted = false) else it
                                            }
                                            currentViewModel.updateNotesList(updatedNotes)
                                            selectedIds = emptySet()
                                            topToastMessage = "笔记已成功恢复"
                                            showTopToast = true
                                        } else {
                                            topToastMessage = "请先点击选择"
                                            showTopToast = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(if (selectedIds.isEmpty()) "恢复" else "恢复选中 (${selectedIds.size})")
                                }
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    onClick = {
                                        val updatedNotes = currentNotes.filterNot { it.isDeleted }
                                        currentViewModel.updateNotesList(updatedNotes)
                                        selectedIds = emptySet()
                                        topToastMessage = "回收站已清空"
                                        showTopToast = true
                                    }
                                ) { Text("清空") }
                            }
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showTopToast,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }) + androidx.compose.animation.fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp)
                    .zIndex(100f)
            ) {
                Box(
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(horizontal = 24.dp)
                        .graphicsLayer {
                            shadowElevation = 12.dp.toPx()
                            shape = RoundedCornerShape(28.dp)
                            clip = true
                        }
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .border(width = 1.dp, color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(28.dp))
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = topToastMessage, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        onDispose { floatingUI.value = null }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 120.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            var currentYear = ""
            trashNotes.forEach { note ->
                val noteYear = formatTime(note.createdAt, "yyyy年")
                if (noteYear != currentYear) { currentYear = noteYear; item { YearHeader(year = noteYear) } }
                item(key = note.id) {
                    val isSelected = selectedIds.contains(note.id)
                    Box(modifier = Modifier.fillMaxWidth().clickable { selectedIds = if (isSelected) selectedIds - note.id else selectedIds + note.id }) {
                        NoteCardThumbnail(note = note, showDate = showDate, onClick = { selectedIds = if (isSelected) selectedIds - note.id else selectedIds + note.id })
                        if (isSelected) Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape))
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 8.dp
        ) {
            Text(
                text = "回收站", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)
            )
        }
    }
}