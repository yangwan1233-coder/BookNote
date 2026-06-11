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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    BackHandler {
        when {
            expandTheme -> expandTheme = false
            expandBackup -> expandBackup = false
            else -> navController.navigate("home") { launchSingleTop = true }
        }
    }

    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION); sharedPref.edit().putString("storage_uri", uri.toString()).apply(); Toast.makeText(context, "存储路径已迁移！", Toast.LENGTH_SHORT).show() }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) { isProcessing = true; expandBackup = false; val success = backupDataToZip(context, uri, notes); withContext(Dispatchers.Main) { isProcessing = false; Toast.makeText(context, if (success) "备份成功" else "备份失败", Toast.LENGTH_SHORT).show() } }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) { isProcessing = true; expandBackup = false; val restoredNotes = restoreDataFromZip(context, uri); withContext(Dispatchers.Main) { if (restoredNotes != null) { notes.clear(); notes.addAll(restoredNotes as Collection<Note>); scope.launch { saveNotesToDisk(context, notes) }; Toast.makeText(context, "成功恢复！", Toast.LENGTH_SHORT).show() } else Toast.makeText(context, "恢复失败", Toast.LENGTH_SHORT).show(); isProcessing = false } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) { try { expandBackup = false; context.contentResolver.openInputStream(uri)?.use { ins -> val content = ins.bufferedReader().readText(); withContext(Dispatchers.Main) { notes.add(Note(title = "导入文档", content = content)); scope.launch { saveNotesToDisk(context, notes) }; Toast.makeText(context, "导入成功！", Toast.LENGTH_SHORT).show() } } } catch (e: Exception) { withContext(Dispatchers.Main) { Toast.makeText(context, "导入失败", Toast.LENGTH_SHORT).show() } } }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp))

            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 8.dp) {
                Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (isProcessing) CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { navController.navigate("archive") }, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary), elevation = ButtonDefaults.buttonElevation(8.dp)) { Text("归档箱") }
                Button(onClick = { navController.navigate("trash") }, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), elevation = ButtonDefaults.buttonElevation(8.dp)) { Text("回收站") }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), elevation = CardDefaults.cardElevation(8.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📁 自定义系统存储", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = if (currentStorageUri.isNotEmpty()) "状态：已绑定系统外置公开文件夹，0应用占用" else "状态：当前默认存储在 App 沙盒内部", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { storageLauncher.launch(null) }) { Text("选择系统公开文件夹") }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().height(56.dp), shadowElevation = 8.dp) {
                Row(modifier = Modifier.padding(horizontal = 24.dp).fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("显示笔记日期", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Switch(checked = showDate, onCheckedChange = onShowDateChange)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // ================= 动画模块 1：个性化主题 =================
            AnimatedContent(
                targetState = expandTheme,
                label = "ThemeTransform",
                transitionSpec = {
                    (fadeIn(tween(300)) + scaleIn(initialScale = 0.8f, animationSpec = tween(300, easing = FastOutSlowInEasing)))
                        .togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 0.8f, animationSpec = tween(200)))
                        // 【极其关键的优化】：加入 SizeTransform 让容器的高度平滑展开，拒绝生硬跳跃！
                        .using(SizeTransform(clip = false))
                }
            ) { isExpanded ->
                if (isExpanded) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 8.dp
                    ) {
                        val initPri = ThemeStore.getPrimaryColor(context)
                        val initSec = ThemeStore.getSecondaryColor(context)
                        var pR by remember { mutableFloatStateOf(initPri.red * 255f) }
                        var pG by remember { mutableFloatStateOf(initPri.green * 255f) }
                        var pB by remember { mutableFloatStateOf(initPri.blue * 255f) }
                        var sR by remember { mutableFloatStateOf(initSec.red * 255f) }
                        var sG by remember { mutableFloatStateOf(initSec.green * 255f) }
                        var sB by remember { mutableFloatStateOf(initSec.blue * 255f) }

                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("莫奈印象派配色", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Surface(onClick = { expandTheme = false }, shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                                    Icon(Icons.Default.Palette, null, modifier = Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(ThemeStore.MonetWaterLily, ThemeStore.MonetSunrise, ThemeStore.MonetLavender, ThemeStore.MonetHaystacks, ThemeStore.MonetGarden).forEach { color ->
                                    Box(modifier = Modifier.size(36.dp).background(color, CircleShape).clickable(
                                        interactionSource = remember { MutableInteractionSource() }, indication = null // 去除点击涟漪，防止视觉干扰
                                    ) {
                                        pR = color.red * 255f; pG = color.green * 255f; pB = color.blue * 255f
                                        // 【防崩溃保护】：安全限制浮点数计算边界
                                        sR = ((color.red * 255f) * 0.85f).coerceIn(0f, 255f)
                                        sG = ((color.green * 255f) * 0.85f).coerceIn(0f, 255f)
                                        sB = ((color.blue * 255f) * 0.85f).coerceIn(0f, 255f)
                                    })
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(24.dp).background(Color(pR.toInt(), pG.toInt(), pB.toInt(), 255), CircleShape)); Spacer(modifier = Modifier.width(8.dp))
                                Text("主色调", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(value = pR, onValueChange = { pR = it }, valueRange = 0f..255f, colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red.copy(alpha=0.5f)))
                            Slider(value = pG, onValueChange = { pG = it }, valueRange = 0f..255f, colors = SliderDefaults.colors(thumbColor = Color.Green, activeTrackColor = Color.Green.copy(alpha=0.5f)))
                            Slider(value = pB, onValueChange = { pB = it }, valueRange = 0f..255f, colors = SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue.copy(alpha=0.5f)))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(24.dp).background(Color(sR.toInt(), sG.toInt(), sB.toInt(), 255), CircleShape)); Spacer(modifier = Modifier.width(8.dp))
                                Text("次色调", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            }
                            Slider(value = sR, onValueChange = { sR = it }, valueRange = 0f..255f, colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red.copy(alpha=0.3f)))
                            Slider(value = sG, onValueChange = { sG = it }, valueRange = 0f..255f, colors = SliderDefaults.colors(thumbColor = Color.Green, activeTrackColor = Color.Green.copy(alpha=0.3f)))
                            Slider(value = sB, onValueChange = { sB = it }, valueRange = 0f..255f, colors = SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue.copy(alpha=0.3f)))

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    ThemeStore.saveColors(context, Color(pR.toInt(), pG.toInt(), pB.toInt(), 255), Color(sR.toInt(), sG.toInt(), sB.toInt(), 255))
                                    Toast.makeText(context, "莫奈色彩已保存，重启生效", Toast.LENGTH_SHORT).show()
                                    expandTheme = false
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp), shape = CircleShape, elevation = ButtonDefaults.buttonElevation(4.dp)
                            ) { Text("保存并应用") }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    ThemeStore.setUseDynamicColor(context, true)
                                    Toast.makeText(context, "已恢复系统壁纸跟随，重启生效", Toast.LENGTH_SHORT).show()
                                    expandTheme = false
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp), shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                elevation = ButtonDefaults.buttonElevation(4.dp)
                            ) { Text("恢复默认主题 (跟随壁纸)") }
                        }
                    }
                } else {
                    Surface(
                        onClick = { expandTheme = true; scope.launch{ scrollState.animateScrollTo(1000) } },
                        shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("个性化主题配色", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    (fadeIn(tween(300)) + scaleIn(initialScale = 0.8f, animationSpec = tween(300, easing = FastOutSlowInEasing)))
                        .togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 0.8f, animationSpec = tween(200)))
                        .using(SizeTransform(clip = false)) // 同样增加防生硬跳变锁
                }
            ) { isExpanded ->
                if (isExpanded) {
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 8.dp) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("数据备份与恢复", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Surface(onClick = { expandBackup = false }, shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha=0.1f)) {
                                    Icon(Icons.Default.CloudSync, null, modifier = Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = { exportLauncher.launch("BookNote_Full_Backup_${System.currentTimeMillis()}.zip") }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = CircleShape, elevation = ButtonDefaults.buttonElevation(4.dp)) { Text("1. 备份所有笔记与图片 (ZIP)") }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { restoreLauncher.launch(arrayOf("application/zip")) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary), elevation = ButtonDefaults.buttonElevation(4.dp)) { Text("2. 恢复完整备份 (ZIP)") }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { importLauncher.launch(arrayOf("text/plain", "text/markdown", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary), elevation = ButtonDefaults.buttonElevation(4.dp)) { Text("3. 导入纯文本 (txt / md)") }
                        }
                    }
                } else {
                    Surface(
                        onClick = { expandBackup = true; scope.launch{ scrollState.animateScrollTo(2000) } },
                        shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("数据备份与恢复", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(120.dp)) // 底部留白防遮挡
        }
    }
}