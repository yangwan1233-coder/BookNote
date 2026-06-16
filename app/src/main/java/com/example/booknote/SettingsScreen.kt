package com.example.booknote

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(notes: MutableList<Note>, showDate: Boolean, navController: NavHostController, onShowDateChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("booknote_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val currentStorageUri = sharedPref.getString("storage_uri", null) ?: ""

    var expandBackup by remember { mutableStateOf(false) }
    var expandTheme by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    // ================= 弹窗提示状态管控 =================
    var showBackupProgress by remember { mutableStateOf(false) }
    var showBackupSuccess by remember { mutableStateOf(false) }
    var backupSizeText by remember { mutableStateOf("") }

    var showRestoreProgress by remember { mutableStateOf(false) }
    var showRestoreSuccess by remember { mutableStateOf(false) }

    BackHandler {
        when {
            showBackupProgress || showRestoreProgress -> { /* 拦截返回键，强制等待进度完成 */ }
            expandTheme -> expandTheme = false
            expandBackup -> expandBackup = false
            else -> navController.navigate("home") { launchSingleTop = true }
        }
    }

    // 【极客修复1：完美安全迁移，绝不乱删核心文件】
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // 父协程统一跑在主线程，控制 UI 状态更安全
            scope.launch {
                isProcessing = true
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                    // 切换到 IO 线程进行高强度文件读写
                    withContext(Dispatchers.IO) {
                        val destDir = DocumentFile.fromTreeUri(context, uri)
                        val imagesDir = destDir?.createDirectory("images") ?: destDir

                        val filesToDelete = mutableListOf<File>() // 【安全锁】：用于精确记录已成功迁移的废弃图片

                        val updatedNotes = notes.map { note ->
                            val newImagePaths = note.imagePaths.map { path ->
                                if (path.contains(context.filesDir.absolutePath)) {
                                    val file = File(path.replace("file://", ""))
                                    if (file.exists()) {
                                        val newFile = imagesDir?.createFile("image/jpeg", file.name)
                                        if (newFile != null) {
                                            context.contentResolver.openOutputStream(newFile.uri)?.use { outStream ->
                                                file.inputStream().use { inStream -> inStream.copyTo(outStream) }
                                            }
                                            filesToDelete.add(file) // 只有真正拷贝成功了，才加入待删除名单
                                            newFile.uri.toString()
                                        } else path
                                    } else path
                                } else path
                            }
                            note.copy(imagePaths = newImagePaths)
                        }

                        // 同步到 UI 数据源（切回主线程）
                        withContext(Dispatchers.Main) {
                            notes.clear()
                            notes.addAll(updatedNotes)
                        }

                        sharedPref.edit().putString("storage_uri", uri.toString()).apply()
                        saveNotesToDisk(context, notes.toList())

                        // 【精确击杀】：安全删除已被成功转移的沙盒图片，绝不破坏 App 其余数据
                        filesToDelete.forEach { it.delete() }
                    }
                    Toast.makeText(context, "存储路径已迁移！沙盒空间已彻底释放", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "迁移失败：${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessing = false // finally 确保加载动画 100% 被关闭
                }
            }
        }
    }

    // 【极客修复2：安全备份与严格的 UI 状态流转】
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch {
                showBackupProgress = true
                expandBackup = false
                try {
                    // IO 计算结果返回给主线程
                    val (success, sizeText) = withContext(Dispatchers.IO) {
                        val isSuccess = backupDataToZip(context, uri, notes.toList())
                        var text = ""
                        if (isSuccess) {
                            var sizeBytes = 0L
                            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                                sizeBytes = fd.statSize
                            }
                            val sizeMb = sizeBytes / (1024 * 1024)
                            val sizeKb = sizeBytes / 1024
                            text = if (sizeMb > 0) "${sizeMb}mb" else "${sizeKb}kb"
                        }
                        isSuccess to text
                    }

                    if (success) {
                        backupSizeText = sizeText
                        showBackupSuccess = true
                    } else {
                        Toast.makeText(context, "备份失败，请检查空间", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "备份出错：${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    showBackupProgress = false // 绝对安全地解除屏幕锁死
                }
            }
        }
    }

    // 【极客修复3：确保时间戳和图片的高精度覆盖与 UI 及时刷新】
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                showRestoreProgress = true
                expandBackup = false
                try {
                    val restoredNotes = withContext(Dispatchers.IO) { restoreDataFromZip(context, uri) }

                    if (restoredNotes != null && (restoredNotes as Collection<Note>).isNotEmpty()) {
                        val backupMap = restoredNotes.associateBy { it.id }
                        val existingIds = notes.map { it.id }.toSet()

                        // 生成一个全新的更新列表，这能确保 Compose 的 UI 重组机制被 100% 触发
                        val updatedList = notes.map { currentNote ->
                            backupMap[currentNote.id] ?: currentNote // 如果备份包里有，直接用备份包的（保留原始创建/编辑时间）
                        }.toMutableList()

                        // 追加纯新增的笔记
                        val newNotes = restoredNotes.filter { it.id !in existingIds }
                        updatedList.addAll(newNotes)

                        notes.clear()
                        notes.addAll(updatedList)

                        withContext(Dispatchers.IO) { saveNotesToDisk(context, notes.toList()) }
                        showRestoreSuccess = true
                    } else {
                        Toast.makeText(context, "备份文件无效或为空", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "恢复出错：${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    showRestoreProgress = false // 绝对安全地解除屏幕锁死
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                isProcessing = true
                expandBackup = false
                try {
                    val content = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { ins ->
                            ins.bufferedReader().readText()
                        }
                    }
                    if (content != null) {
                        notes.add(Note(title = "导入文档", content = content))
                        withContext(Dispatchers.IO) { saveNotesToDisk(context, notes.toList()) }
                        Toast.makeText(context, "纯文本导入成功！", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "导入失败：格式不支持", Toast.LENGTH_SHORT).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // 【优化边界规避】：彻底抛弃低级且易崩溃的 WindowInsets.statusBars 手工运算
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 8.dp
            ) {
                Text(
                    "Settings",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (isProcessing) CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { navController.navigate("archive") },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    elevation = ButtonDefaults.buttonElevation(8.dp)
                ) { Text("归档箱") }
                Button(
                    onClick = { navController.navigate("trash") },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    elevation = ButtonDefaults.buttonElevation(8.dp)
                ) { Text("回收站") }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "📁 自定义系统存储",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (currentStorageUri.isNotEmpty()) "状态：已绑定系统外置公开文件夹，0应用占用" else "状态：当前默认存储在 App 沙盒内部",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { storageLauncher.launch(null) }) { Text("选择系统公开文件夹") }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp).fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "显示笔记日期",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(checked = showDate, onCheckedChange = onShowDateChange)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // ================= 动画模块 1：个性化主题 =================
            AnimatedContent(
                targetState = expandTheme,
                label = "ThemeTransform",
                transitionSpec = {
                    (fadeIn(tween(300)) + scaleIn(
                        initialScale = 0.8f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ))
                        .togetherWith(
                            fadeOut(tween(200)) + scaleOut(
                                targetScale = 0.8f,
                                animationSpec = tween(200)
                            )
                        )
                        .using(SizeTransform(clip = false))
                }
            ) { isExpanded ->
                if (isExpanded) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 8.dp
                    ) {
                        val initPri = ThemeStore.getPrimaryColor(context)
                        val initSec = ThemeStore.getSecondaryColor(context)
                        var pR by remember { mutableFloatStateOf(initPri.red * 255f) }
                        var pG by remember { mutableFloatStateOf(initPri.green * 255f) }
                        var pB by remember { mutableFloatStateOf(initPri.blue * 255f) }
                        var sR by remember { mutableFloatStateOf(initSec.red * 255f) }
                        var sG by remember { mutableFloatStateOf(initSec.green * 255f) }
                        var sB by remember { mutableFloatStateOf(initSec.blue * 255f) }

                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "莫奈印象派配色",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    onClick = { expandTheme = false },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ) {
                                    Icon(
                                        Icons.Default.Palette,
                                        null,
                                        modifier = Modifier.padding(8.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(
                                    ThemeStore.MonetWaterLily,
                                    ThemeStore.MonetSunrise,
                                    ThemeStore.MonetLavender,
                                    ThemeStore.MonetHaystacks,
                                    ThemeStore.MonetGarden
                                ).forEach { color ->
                                    Box(
                                        modifier = Modifier.size(36.dp)
                                            .background(color, CircleShape).clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                pR = color.red * 255f; pG = color.green * 255f; pB =
                                                color.blue * 255f
                                                sR = ((color.red * 255f) * 0.85f).coerceIn(0f, 255f)
                                                sG = ((color.green * 255f) * 0.85f).coerceIn(
                                                    0f,
                                                    255f
                                                )
                                                sB =
                                                    ((color.blue * 255f) * 0.85f).coerceIn(0f, 255f)
                                            })
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(24.dp).background(
                                        Color(pR.toInt(), pG.toInt(), pB.toInt(), 255),
                                        CircleShape
                                    )
                                ); Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "主色调",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = pR,
                                onValueChange = { pR = it },
                                valueRange = 0f..255f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.Red,
                                    activeTrackColor = Color.Red.copy(alpha = 0.5f)
                                )
                            )
                            Slider(
                                value = pG,
                                onValueChange = { pG = it },
                                valueRange = 0f..255f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.Green,
                                    activeTrackColor = Color.Green.copy(alpha = 0.5f)
                                )
                            )
                            Slider(
                                value = pB,
                                onValueChange = { pB = it },
                                valueRange = 0f..255f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.Blue,
                                    activeTrackColor = Color.Blue.copy(alpha = 0.5f)
                                )
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(24.dp).background(
                                        Color(sR.toInt(), sG.toInt(), sB.toInt(), 255),
                                        CircleShape
                                    )
                                ); Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "次色调",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Slider(
                                value = sR,
                                onValueChange = { sR = it },
                                valueRange = 0f..255f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.Red,
                                    activeTrackColor = Color.Red.copy(alpha = 0.3f)
                                )
                            )
                            Slider(
                                value = sG,
                                onValueChange = { sG = it },
                                valueRange = 0f..255f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.Green,
                                    activeTrackColor = Color.Green.copy(alpha = 0.3f)
                                )
                            )
                            Slider(
                                value = sB,
                                onValueChange = { sB = it },
                                valueRange = 0f..255f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.Blue,
                                    activeTrackColor = Color.Blue.copy(alpha = 0.3f)
                                )
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    ThemeStore.saveColors(
                                        context,
                                        Color(pR.toInt(), pG.toInt(), pB.toInt(), 255),
                                        Color(sR.toInt(), sG.toInt(), sB.toInt(), 255)
                                    )
                                    Toast.makeText(
                                        context,
                                        "莫奈色彩已保存，重启生效",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    expandTheme = false
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = CircleShape,
                                elevation = ButtonDefaults.buttonElevation(4.dp)
                            ) { Text("保存并应用") }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    ThemeStore.setUseDynamicColor(context, true)
                                    Toast.makeText(
                                        context,
                                        "已恢复系统壁纸跟随，重启生效",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    expandTheme = false
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                elevation = ButtonDefaults.buttonElevation(4.dp)
                            ) { Text("恢复默认主题 (跟随壁纸)") }
                        }
                    }
                } else {
                    Surface(
                        onClick = {
                            expandTheme = true; scope.launch {
                            scrollState.animateScrollTo(
                                1000
                            )
                        }
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "个性化主题配色",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // ================= 动画模块 2：数据备份 =================
            AnimatedContent(
                targetState = expandBackup,
                label = "BackupTransform",
                transitionSpec = {
                    (fadeIn(tween(300)) + scaleIn(
                        initialScale = 0.8f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ))
                        .togetherWith(
                            fadeOut(tween(200)) + scaleOut(
                                targetScale = 0.8f,
                                animationSpec = tween(200)
                            )
                        )
                        .using(SizeTransform(clip = false))
                }
            ) { isExpanded ->
                if (isExpanded) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "数据备份与恢复",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    onClick = { expandBackup = false },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ) {
                                    Icon(
                                        Icons.Default.CloudSync,
                                        null,
                                        modifier = Modifier.padding(8.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { exportLauncher.launch("BookNote_Full_Backup_${System.currentTimeMillis()}.zip") },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = CircleShape,
                                elevation = ButtonDefaults.buttonElevation(4.dp)
                            ) { Text("1. 备份所有笔记与图片 (ZIP)") }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { restoreLauncher.launch(arrayOf("application/zip")) },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                elevation = ButtonDefaults.buttonElevation(4.dp)
                            ) { Text("2. 恢复完整备份 (ZIP)") }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    importLauncher.launch(
                                        arrayOf(
                                            "text/plain",
                                            "text/markdown",
                                            "application/octet-stream"
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                elevation = ButtonDefaults.buttonElevation(4.dp)
                            ) { Text("3. 导入纯文本 (txt / md)") }
                        }
                    }
                } else {
                    Surface(
                        onClick = {
                            expandBackup = true; scope.launch {
                            scrollState.animateScrollTo(
                                2000
                            )
                        }
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "数据备份与恢复",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // 将两个按钮合为一个 Surface 背景
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp), // 高度精确修改为 56.dp
                shape = RoundedCornerShape(32.dp), // 圆角代码和数值严格保持不变
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 8.dp // 阴影代码和数值严格保持不变
            ) {
                // 内部使用 Row 等分两个点击区域
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ---------------- 1. 待办按钮区 ----------------
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { navController.navigate("todo_screen") },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "待办",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp)) // 改为横向间距
                        Text("待办", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // ---------------- 优雅的居中分割线 ----------------
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight(0.4f)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    )

                    // ---------------- 2. 更多设置按钮区 ----------------
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { navController.navigate("more_settings_screen") },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "更多设置",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("更多设置", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(120.dp))
            // ================= 全局弹窗 UI 渲染层 =================

            // 1. 备份进度防误触遮罩
            if (showBackupProgress) {
                AlertDialog(
                    onDismissRequest = { },
                    properties = DialogProperties(
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false
                    ),
                    title = { Text("系统提示", fontWeight = FontWeight.Bold) },
                    text = { Text("正在备份中 请不要离开此界面", fontSize = 16.sp) },
                    confirmButton = {}
                )
            }

            // 2. 备份完成提示框
            if (showBackupSuccess) {
                AlertDialog(
                    onDismissRequest = { showBackupSuccess = false },
                    title = { Text("操作成功", fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "备份完成 总共${backupSizeText} 请自行保存好备份包",
                            fontSize = 16.sp
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showBackupSuccess = false }) { Text("确认") }
                    }
                )
            }

            // 3. 恢复进度防误触遮罩
            if (showRestoreProgress) {
                AlertDialog(
                    onDismissRequest = { },
                    properties = DialogProperties(
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false
                    ),
                    title = { Text("系统提示", fontWeight = FontWeight.Bold) },
                    text = { Text("恢复中 请不要离开此界面", fontSize = 16.sp) },
                    confirmButton = {}
                )
            }

            // 4. 恢复完成提示框
            if (showRestoreSuccess) {
                AlertDialog(
                    onDismissRequest = { showRestoreSuccess = false },
                    title = { Text("操作成功", fontWeight = FontWeight.Bold) },
                    text = { Text("恢复完成", fontSize = 16.sp) },
                    confirmButton = {
                        TextButton(onClick = { showRestoreSuccess = false }) { Text("确认") }
                    }
                )
            }
        }
    }
}