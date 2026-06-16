package com.example.booknote

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
fun MoreSettingsScreen(navController: NavHostController) {
    val context = LocalContext.current

    // 【高级状态锁】：用于管控当系统静默拦截添加时，弹出的“去手动开启权限”的引导对话框
    var showPermissionDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ================= 1. 顶部标题 =================
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 8.dp) {
                Text(text = "更多设置", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ================= 2. 桌面待办部件 (2x2) 预览与添加 =================
            Text(text = "点击下方部件将其添加至桌面", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(8.dp)) // 强制缩小间距

            // 移除 weight(1f)，让 Box 紧贴文字，不由于权重而撑开
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val scaleRatio = 0.8f // 预览比例

                Surface(
                    modifier = Modifier
                        .graphicsLayer { scaleX = scaleRatio; scaleY = scaleRatio }
                        .requiredSize(430.dp)
                        .clip(RoundedCornerShape(73.dp))
                        .clickable {
                            // 执行具备权限穿透的高级小部件请求函数
                            val needManualPermission = requestPinTodoShortcut(context)
                            if (needManualPermission) {
                                showPermissionDialog = true // 激活去开启权限的引导弹窗
                            }
                        },
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shadowElevation = 16.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(73.dp).background(Color(0xFFFFD54F)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.width(73.dp))
                            Text(text = "待办事项", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.weight(1f), textAlign = TextAlign.Left)
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

        // ================= 3. 底部返回按钮 (对标主页底部导航栏样式) =================
        // 【核心修改】：将全面屏动态适配与绝对锚点精准赋给外层 Box 容器
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter) // 【核心1：绝对锚点】
                .fillMaxWidth()                // 【核心2：横向占满】
                .padding(                      // 【核心3：安全区与呼吸距】
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 8.dp,
                tonalElevation = 8.dp, // 增加色调高度以增强悬浮感
                modifier = Modifier
                    .width(140.dp)
                    .height(56.dp)
                    .shadow(8.dp, CircleShape) // 明确增加 shadow 修饰符，防止 Surface 阴影被裁切
                    .clip(CircleShape)
                    .clickable { navController.popBackStack() }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "返回设置", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    // ================= 权限引导核心弹窗 UI 层 =================
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要开启桌面小部件权限", fontWeight = FontWeight.Bold) },
            text = { Text("由于系统安全拦截，请在接下来的设置界面中，手动将“创建桌面快捷方式”或“添加桌面小部件”权限勾选为“允许”。", fontSize = 15.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        // 【大厂级黑科技】：一键穿透直达系统当前的 App 权限设置管理界面，绝不让用户迷路
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
                ) {
                    Text("去开启权限")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("取消")
                }
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

// ================= 核心重构：具有异常监控感知能力的系统唤起函数 =================
// 返回值 Boolean：代表系统是否判定需要弹窗引导用户手动解锁权限
fun requestPinTodoShortcut(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val myProvider = ComponentName(context, TodoWidgetReceiver::class.java)

        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            return try {
                // 【核心优化】：执行原生请求
                val isRequested = appWidgetManager.requestPinAppWidget(myProvider, null, null)

                if (isRequested) {
                    Toast.makeText(context, "已发送请求，请留意系统弹窗...", Toast.LENGTH_SHORT).show()
                    false // 成功唤起，不需要弹窗引导
                } else {
                    // 如果返回 false，说明系统底层直接静默回绝了，必须弹窗去引导用户开启权限
                    true
                }
            } catch (e: Exception) {
                true // 抛出异常同样意味着被系统安全沙盒阻断，返回 true 触发引导
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