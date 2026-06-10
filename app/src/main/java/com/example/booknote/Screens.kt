package com.example.booknote

import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Context
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

@Composable
fun HomeScreen(notes: MutableList<Note>, showDate: Boolean, navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val displayNotes = notes.filter { !it.isArchived && !it.isDeleted }.sortedBy { it.createdAt }
    val listState = rememberLazyListState()

    var backPressedTime by remember { mutableStateOf(0L) }
    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime < 2000) { (context as? Activity)?.finish() }
        else { backPressedTime = currentTime; Toast.makeText(context, "再按一次退出应用", Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(displayNotes.size) { if (displayNotes.isNotEmpty()) listState.animateScrollToItem(displayNotes.size) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 120.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            var currentYear = ""
            displayNotes.forEach { note ->
                val noteYear = formatTime(note.createdAt, "yyyy年")
                if (noteYear != currentYear) { currentYear = noteYear; item { YearHeader(year = noteYear) } }
                item(key = note.id) {
                    SwipeHoverNoteCard(
                        note = note, showDate = showDate, onClick = { navController.navigate("edit/${note.id}") },
                        onArchive = {
                            val idx = notes.indexOfFirst { it.id == note.id }
                            if (idx >= 0) { notes[idx] = notes[idx].copy(isArchived = true); scope.launch { saveNotesToDisk(context, notes) } }
                        },
                        onDelete = {
                            val idx = notes.indexOfFirst { it.id == note.id }
                            if (idx >= 0) { notes[idx] = notes[idx].copy(isDeleted = true); scope.launch { saveNotesToDisk(context, notes) } }
                        }
                    )
                }
            }
        }
        // 【阴影统一】主页顶部的 BookNote 标题岛屿，阴影由 6.dp 提升至 8.dp
        Surface(modifier = Modifier.align(Alignment.TopCenter).padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), shadowElevation = 8.dp) {
            Text("BookNote", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp))
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EditNoteScreen(navController: NavHostController, noteId: String, notes: MutableList<Note>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentNote = notes.find { it.id == noteId } ?: Note()

    var title by remember { mutableStateOf(currentNote.title) }
    var content by remember { mutableStateOf(TextFieldValue(currentNote.content)) }
    var imagePaths by remember { mutableStateOf(currentNote.imagePaths) }

    var undoStack by remember { mutableStateOf(listOf(TextFieldValue(currentNote.content))) }
    var redoStack by remember { mutableStateOf(listOf<TextFieldValue>()) }

    // 【修改点1】将全屏图片URL改为全屏图片索引 (Index)，用于支持左右滑动
    var fullScreenImageIndex by remember { mutableStateOf<Int?>(null) }
    var imageToDelete by remember { mutableStateOf<String?>(null) }
    var showDates by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(10000); showDates = false }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        if (uris.isNotEmpty()) {
            val newPaths = uris.take(9 - imagePaths.size).mapNotNull { copyUriToSystemStorage(context, it) }
            imagePaths = imagePaths + newPaths
        }
    }

    val onBack = {
        val isEmpty = title.isBlank() && content.text.isBlank() && imagePaths.isEmpty()
        if (isEmpty) {
            notes.removeAll { it.id == currentNote.id }
            Toast.makeText(context, "空白文档已舍弃", Toast.LENGTH_SHORT).show()
        } else {
            val index = notes.indexOfFirst { it.id == currentNote.id }
            val updatedNote = currentNote.copy(title = title, content = content.text, imagePaths = imagePaths, updatedAt = System.currentTimeMillis())
            if (index >= 0) notes[index] = updatedNote else notes.add(updatedNote)
            scope.launch { saveNotesToDisk(context, notes) }
            Toast.makeText(context, "内容已保存", Toast.LENGTH_SHORT).show()
        }
        navController.popBackStack()
    }
    BackHandler { onBack() }

    Box(modifier = Modifier.fillMaxSize().imePadding()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 80.dp))

            // 【修改点2】点击图片时，传入该图片在九宫格中的索引位置
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

            TextField(
                value = title, onValueChange = { if (it.length <= 100) title = it }, placeholder = { Text("输入标题", fontSize = 28.sp, fontWeight = FontWeight.Bold) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent), modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = content,
                onValueChange = {
                    content = it
                    if (undoStack.lastOrNull()?.text != it.text) {
                        undoStack = (undoStack + it).takeLast(30)
                        redoStack = emptyList()
                    }
                },
                placeholder = { Text("开始记录...", fontSize = 16.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent), modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 400.dp)
            )
            Spacer(modifier = Modifier.height(140.dp))
        }

        // 顶部悬浮操作栏：返回与保存按钮 (保留了您刚加的 8.dp 阴影)
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
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp), contentAlignment = Alignment.Center) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f), shadowElevation = 8.dp) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                            } else {
                                Toast.makeText(context, "请先长按滑动选中需要加列表符号的几行文字", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            undoStack = (undoStack + content).takeLast(30)
                            redoStack = emptyList()
                        }) {
                            Icon(Icons.Default.List, "列表排列", tint = MaterialTheme.colorScheme.primary)
                        }

                        IconButton(onClick = {
                            if (undoStack.size > 1) {
                                redoStack = redoStack + undoStack.last()
                                undoStack = undoStack.dropLast(1)
                                content = undoStack.last()
                            } else Toast.makeText(context, "无法撤销了", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.Undo, "撤销上一步", tint = MaterialTheme.colorScheme.primary) }

                        IconButton(onClick = {
                            if (redoStack.isNotEmpty()) {
                                val next = redoStack.last()
                                redoStack = redoStack.dropLast(1)
                                undoStack = undoStack + next
                                content = next
                            } else Toast.makeText(context, "无法重做了", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.Redo, "重做下一步", tint = MaterialTheme.colorScheme.primary) }

                        IconButton(onClick = {
                            val selStart = content.selection.start
                            content = TextFieldValue(content.text.substring(0, selStart) + "()" + content.text.substring(content.selection.end), selection = TextRange(selStart + 1))
                            undoStack = (undoStack + content).takeLast(30); redoStack = emptyList()
                        }) { Text("()", fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary) }

                        IconButton(onClick = {
                            if (imagePaths.size < 9) photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            else Toast.makeText(context, "最多9张", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.Image, "插入图片", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }
    }

    // 【修改点3】终极画廊模式：支持左右丝滑滑动、顶部动态页码、底部裁剪悬浮舱
    if (fullScreenImageIndex != null) {
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = fullScreenImageIndex!!,
            pageCount = { imagePaths.size }
        )

        Dialog(onDismissRequest = { fullScreenImageIndex = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f))) {

                // 1. 左右滑动分页引擎
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

                // 2. 顶部居中指示器：同款圆角阴影胶囊
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

                // 3. 底部居中裁剪引擎：同款圆角阴影胶囊，唤醒系统级编辑器
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.clickable {
                            // 【黑科技】唤醒手机自带的原生图像编辑器进行裁剪，0 内存占用！
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
fun SettingsScreen(notes: MutableList<Note>, showDate: Boolean, navController: NavHostController, onShowDateChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("booknote_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    val currentStorageUri = sharedPref.getString("storage_uri", null) ?: ""

    val storageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            sharedPref.edit().putString("storage_uri", uri.toString()).apply()
            Toast.makeText(context, "存储路径已成功迁移至外置系统文件夹！", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler { navController.navigate("home") { launchSingleTop = true } }

    var isProcessing by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            isProcessing = true
            val success = backupDataToZip(context, uri, notes)
            isProcessing = false
            Toast.makeText(context, if (success) "完整备份成功！" else "备份失败", Toast.LENGTH_SHORT).show()
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            isProcessing = true
            val restoredNotes = restoreDataFromZip(context, uri)
            if (restoredNotes != null) {
                notes.clear(); notes.addAll(restoredNotes)
                saveNotesToDisk(context, notes)
                Toast.makeText(context, "成功恢复 ${restoredNotes.size} 条笔记(含图片)！", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(context, "恢复失败，文件损坏或格式不对", Toast.LENGTH_SHORT).show()
            isProcessing = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    notes.add(Note(title = "导入文档_${System.currentTimeMillis() % 1000}", content = ins.bufferedReader().readText()))
                    saveNotesToDisk(context, notes); Toast.makeText(context, "文本导入成功！", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { Toast.makeText(context, "导入失败", Toast.LENGTH_SHORT).show() }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp))

        // 【阴影统一】1. Settings 标题岛屿增加 8.dp 阴影
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 8.dp) {
            Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp))
        }
        Spacer(modifier = Modifier.height(48.dp))

        if (isProcessing) CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { navController.navigate("archive") }, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)) { Text("归档箱") }
            Button(onClick = { navController.navigate("trash") }, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)), elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)) { Text("回收站") }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // 【阴影统一】3. 自定义系统存储卡片增加 8.dp 阴影
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("📁 自定义系统存储", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (currentStorageUri.isNotEmpty()) "状态：已绑定系统外置公开文件夹，0应用占用" else "状态：当前默认存储在 App 沙盒内部",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { storageLauncher.launch(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("选择系统公开文件夹")
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // 【阴影统一】4. 其他基础设置项增加 8.dp 阴影
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().height(56.dp), shadowElevation = 8.dp) {
            Row(modifier = Modifier.padding(horizontal = 24.dp).fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("显示笔记日期", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = showDate, onCheckedChange = onShowDateChange)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { exportLauncher.launch("BookNote_Full_Backup_${System.currentTimeMillis()}.zip") }, modifier = Modifier.fillMaxWidth().height(56.dp), elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)) { Text("1. 备份所有笔记与图片 (ZIP)") }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { restoreLauncher.launch(arrayOf("application/zip")) }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary), elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)) { Text("2. 恢复完整备份 (ZIP)") }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { importLauncher.launch(arrayOf("text/plain", "text/markdown", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary), elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)) { Text("3. 导入纯文本 (txt / md)") }
    }
}

@Composable
fun NoteCardThumbnail(note: Note, showDate: Boolean, onClick: () -> Unit) {
    // 【阴影统一】给每条笔记的缩略图卡片增加 8.dp 阴影
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 18.dp, horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showDate) {
                Text(text = formatTime(note.createdAt), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), modifier = Modifier.padding(end = 12.dp))
            }
            if (note.imagePaths.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f).padding(end = 12.dp)) {
                    AsyncImage(model = Uri.parse(note.imagePaths.first()), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))
                }
            }
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

        Surface(modifier = Modifier.align(Alignment.BottomCenter).wrapContentWidth().padding(bottom = 40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 8.dp) {
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

        Surface(modifier = Modifier.align(Alignment.BottomCenter).wrapContentWidth().padding(bottom = 40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 8.dp) {
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