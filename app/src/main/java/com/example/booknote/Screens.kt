package com.example.booknote

import androidx.compose.ui.focus.onFocusChanged
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.IntrinsicSize
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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

// ================== 第二部分：主页代码 ==================
@Composable
fun HomeScreen(notes: MutableList<Note>, showDate: Boolean, navController: NavHostController) {
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
    var backPressedTime by remember { mutableStateOf(0L) }

    BackHandler {
        if (isSearchExpanded) {
            isSearchExpanded = false
            searchQuery = ""
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - backPressedTime < 2000) { (context as? Activity)?.finish() }
            else { backPressedTime = currentTime; Toast.makeText(context, "再按一次退出应用", Toast.LENGTH_SHORT).show() }
        }
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
                                // 【大厂级动态间距】：
                                // 如果是当月最后一条笔记，收缩间距，只留 2.dp 给下个月的标题；
                                // 如果是普通的中间笔记，依然保持 12.dp 的舒适阅读距离！
                                .padding(bottom = if (isLastNoteInMonth) 12.dp else 12.dp)
                        ) {
                            SwipeHoverNoteCard(
                                note = note,
                                showDate = showDate, // 注意：根据您上面的代码，这里的变量名应该是 showDates
                                onClick = {
                                    keyboardController?.hide()
                                    navController.navigate("edit/${note.id}")
                                },
                                onArchive = {
                                    val idx = notes.indexOfFirst { it.id == note.id }
                                    if (idx >= 0) {
                                        notes[idx] = notes[idx].copy(isArchived = true)
                                        scope.launch { saveNotesToDisk(context, notes) }
                                    }
                                },
                                onDelete = {
                                    val idx = notes.indexOfFirst { it.id == note.id }
                                    if (idx >= 0) {
                                        notes[idx] = notes[idx].copy(isDeleted = true)
                                        scope.launch { saveNotesToDisk(context, notes) }
                                    }
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
fun EditNoteScreen(navController: NavHostController, noteId: String, notes: MutableList<Note>) {
    // 1. 定义富文本与标题的组合快照数据模型

    val redoStack = remember { mutableStateListOf<NoteSnapshot>() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    // 【100% 恢复您的原始保存逻辑，一字未改】
    val onBack = {
        // ==========================================
        // 【核心存储转换】：在存盘前，先把复杂的块结构整理好
        // ==========================================
        // 1. 将所有表格、导图、文字按排版顺序序列化为极其安全的 JSON
        val newBlocksJson = BlockSerializer.serializeBlocks(activeBlocks)

        // 2. 提取出所有文本块的内容拼接成纯文本
        val pureTextPreview = activeBlocks
            .filterIsInstance<UITextBlock>()
            .joinToString("\n") { it.content.text }
            .trim()

        // 3. 【防误删升级】：不仅要判断文字，还要判断是不是插入了纯表格或导图
        val hasRichBlocks = activeBlocks.any { it is UITableBlock || it is UIMindMapBlock }
        val isEmpty = title.isBlank() && pureTextPreview.isBlank() && imagePaths.isEmpty() && !hasRichBlocks

        if (isEmpty) {
            notes.removeAll { it.id == currentNote.id }
            Toast.makeText(context, "空白文档已舍弃", Toast.LENGTH_SHORT).show()
        } else {
            val index = notes.indexOfFirst { it.id == currentNote.id }

            // 4. 更新 Note 数据对象
            val updatedNote = currentNote.copy(
                title = title,
                content = pureTextPreview,  // 护城河：主页的预览依然只显示纯文本！
                blocksJson = newBlocksJson, // 新能力：富文本的终极排版归宿！
                imagePaths = imagePaths,
                updatedAt = System.currentTimeMillis()
            )

            if (index >= 0) notes[index] = updatedNote else notes.add(updatedNote)

            // 指定 IO 线程进行磁盘操作，防止主线程卡顿
            scope.launch(kotlinx.coroutines.Dispatchers.IO) { saveNotesToDisk(context, notes) }
            Toast.makeText(context, "内容已保存", Toast.LENGTH_SHORT).show()
        }
        navController.popBackStack()
    }
    BackHandler { onBack() }

    Box(modifier = Modifier.fillMaxSize().imePadding()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
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

            // 【只修改了这里】：增加了 focusRequester 和 回车键变为 Next 的逻辑
            TextField(
                value = title,
                onValueChange = { if (it.length <= 100) title = it },
                placeholder = { Text("输入标题", fontSize = 28.sp, fontWeight = FontWeight.Bold) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
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
                                color = MaterialTheme.colorScheme.onSurface
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
            FloatingActionButton(
                onClick = { onBack() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                modifier = Modifier.width(80.dp).height(48.dp)
            ) {
                Icon(Icons.Default.ArrowBack, "返回", modifier = Modifier.scale(1.3f))
            }

            FloatingActionButton(
                onClick = { onBack() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                modifier = Modifier.width(80.dp).height(48.dp)
            ) {
                Icon(Icons.Default.Check, "保存", modifier = Modifier.scale(1.3f))
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (showDates) {
                Column(modifier = Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 12.dp), horizontalAlignment = Alignment.End) {
                    Text(text = "创建: ${formatTime(currentNote.createdAt, "yyyy-MM-dd HH:mm")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Text(text = "编辑: ${formatTime(System.currentTimeMillis(), "yyyy-MM-dd HH:mm")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f), shadowElevation = 8.dp) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            val fullText = content.text
                            val start = content.selection.min
                            val end = content.selection.max

                            if (start < end) {
                                val selectedText = fullText.substring(start, end)
                                val bulletedSelection = selectedText.split("\n").joinToString("\n") { line ->
                                    if (line.isNotBlank() && !line.trim().startsWith("•")) "• $line" else line
                                }
                                val newText = fullText.substring(0, start) + bulletedSelection + fullText.substring(end)
                                content = TextFieldValue(text = newText, selection = TextRange(start, start + bulletedSelection.length))
                                Toast.makeText(context, "已将选中段落转化为列表", Toast.LENGTH_SHORT).show()

                                // 【修复】：记录快照
                                captureSnapshot()

                            } else {
                                Toast.makeText(context, "请先长按滑动选中需要加列表符号的几行文字", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.List, "列表排列", tint = MaterialTheme.colorScheme.primary)
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
                            Icon(Icons.Default.Undo, "撤销上一步", tint = MaterialTheme.colorScheme.primary)
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
                            Icon(Icons.Default.Redo, "重做下一步", tint = MaterialTheme.colorScheme.primary)
                        }
                        // ... 您原有的撤销、重做等按钮 ...

                        // =====================================
                        // 【无 Bug 完美替换】：插入功能的联动
                        // =====================================
                        EditorBottomBar(
                            onMindMapClick = {
                                // 【安全计算插入位置】：紧贴着当前光标所在的下一行插入
                                val insertIndex = (focusedBlockIndex + 1).coerceIn(0, activeBlocks.size)

                                val newMap = UIMindMapBlock()
                                val newText = UITextBlock()

                                activeBlocks.add(insertIndex, newMap)
                                activeBlocks.add(insertIndex + 1, newText)

                                focusedBlockIndex = insertIndex + 1
                                selectedMindMapNodeId = newMap.rootNode.id // 自动激活面板
                            },
                            onTableClick = {
                                // 【安全计算插入位置】：紧贴着当前光标所在的下一行插入
                                val insertIndex = (focusedBlockIndex + 1).coerceIn(0, activeBlocks.size)

                                val newTable = UITableBlock()
                                val newText = UITextBlock()

                                activeBlocks.add(insertIndex, newTable)
                                activeBlocks.add(insertIndex + 1, newText)

                                focusedBlockIndex = insertIndex + 1
                            }
                        )


                        IconButton(onClick = {
                            if (imagePaths.size < 9) photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            else Toast.makeText(context, "最多9张", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.Image, "插入图片", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }
        // =====================================
        // 【升级】：极简胶囊形态的导图图标工具栏
        // =====================================
        if (selectedMindMapNodeId != null) {
            val mapIndex = activeBlocks.indexOfFirst { it is UIMindMapBlock && findMindMapNode(it.rootNode, selectedMindMapNodeId!!) != null }
            if (mapIndex != -1) {
                val mapBlock = activeBlocks[mapIndex] as UIMindMapBlock

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 90.dp)
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
                            activeBlocks[mapIndex] = mapBlock.copy(rootNode = addMindMapChild(mapBlock.rootNode, selectedMindMapNodeId!!))
                        }) {
                            Icon(
                                imageVector = Icons.Default.SubdirectoryArrowRight,
                                contentDescription = "加细节",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // 2. 加并列分支 (兄弟节点) - 使用“加号”表示同级新增
                        IconButton(onClick = {
                            activeBlocks[mapIndex] = mapBlock.copy(rootNode = addMindMapSibling(mapBlock.rootNode, selectedMindMapNodeId!!))
                        }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "加并列",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // 3. 删节点 - 使用“垃圾桶”图标，并标红以示警告
                        IconButton(onClick = {
                            val updated = deleteMindMapNode(mapBlock.rootNode, selectedMindMapNodeId!!)
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

        // =====================================
        // 【升级】：插入功能的联动 (让组件插在当前行的下一行！)
        // =====================================
        // 【无 Bug 完美替换】：插入功能的联动

        //
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
fun ArchiveScreen(notes: MutableList<Note>, showDate: Boolean, navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val archiveNotes = notes.filter { it.isArchived && !it.isDeleted }.sortedBy { it.createdAt }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 【核心修改 1】将 top padding 扩大到 120.dp，完美填充标题与日期列表之间的间距，保持与主页一致
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

        // 【核心修改 2】归档箱标题：变成悬浮胶囊！增加与导航栏完全一致的背景色(surfaceVariant)、8.dp阴影和极限圆角
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 8.dp
        ) {
            Text(
                text = "归档箱",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)
            )
        }

        // 【精准修改点 1】：归档箱底部悬浮栏位置完美对齐大厂全面屏规范
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter) // 【核心1：绝对锚点】
                .fillMaxWidth()                // 【核心2：横向占满】
                .wrapContentWidth()            // 【兼顾原有属性】：内容弹性收缩居中，防止胶囊变形拉长
                .padding(                      // 【核心3：安全区与呼吸距】
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                ),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 8.dp
        ) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { navController.popBackStack() }) { Text("返回设置", fontWeight = FontWeight.Bold) }
                Button(onClick = {
                    if (selectedIds.isNotEmpty()) {
                        notes.replaceAll { if (it.id in selectedIds) it.copy(isArchived = false) else it }
                        scope.launch { saveNotesToDisk(context, notes) }
                        selectedIds = emptySet()
                        Toast.makeText(context, "已取消存档", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(context, "请先点击选择", Toast.LENGTH_SHORT).show()
                }) { Text(if (selectedIds.isEmpty()) "取消存档" else "恢复选中 (${selectedIds.size})") }
            }
        }
    }
}

@Composable
fun TrashScreen(notes: MutableList<Note>, showDate: Boolean, navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trashNotes = notes.filter { it.isDeleted }.sortedBy { it.createdAt }

    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 【核心修改 1】将 top padding 扩大到 120.dp
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
                        if (isSelected) {
                            Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape))
                        }
                    }
                }
            }
        }

        // 【核心修改 2】回收站标题：悬浮胶囊化！保留了危险警示的 error 红色字体
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 8.dp
        ) {
            Text(
                text = "回收站",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)
            )
        }

        // 【精准修改点 2】：回收站底部悬浮栏位置完美对齐大厂全面屏规范
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter) // 【核心1：绝对锚点】
                .fillMaxWidth()                // 【核心2：横向占满】
                .wrapContentWidth()            // 【兼顾原有属性】：内容弹性收缩居中，防止胶囊变形拉长
                .padding(                      // 【核心3：安全区与呼吸距】
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                ),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 8.dp
        ) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { navController.popBackStack() }) {
                    Text("返回设置", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (selectedIds.isNotEmpty()) {
                            notes.replaceAll { if (it.id in selectedIds) it.copy(isDeleted = false) else it }
                            scope.launch { saveNotesToDisk(context, notes) }
                            selectedIds = emptySet()
                            Toast.makeText(context, "笔记已成功恢复！", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "请先点击选择要恢复的笔记", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (selectedIds.isEmpty()) "恢复笔记" else "恢复选中 (${selectedIds.size})")
                }

                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        notes.removeAll { it.isDeleted }
                        scope.launch { saveNotesToDisk(context, notes) }
                        selectedIds = emptySet()
                        Toast.makeText(context, "回收站已清空", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("清空回收站")
                }
            }
        }
    }
}