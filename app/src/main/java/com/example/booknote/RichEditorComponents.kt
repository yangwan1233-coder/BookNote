package com.example.booknote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalContext

sealed class UIBlock { val blockId: String = UUID.randomUUID().toString() }
data class UITextBlock(var content: TextFieldValue = TextFieldValue("")) : UIBlock()
data class UITableBlock(var tableData: TableData = TableData()) : UIBlock()

// ================= 大厂级不可变数据模型（解决无法输入 Bug 的核心） =================
data class TableData(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val rows: Int = 2,
    val cols: Int = 2,
    val cells: List<List<String>> = List(2) { List(2) { "" } }
)

data class MindMapNode(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "中心主题",
    val children: List<MindMapNode> = emptyList()
)
data class UIMindMapBlock(
    // 💡 优化 1：【必须】注入唯一 ID！
    // 如果没有独立 ID，Compose 列表滑动时会复用组件，导致您的标题张冠李戴，甚至莫名其妙消失！
    val id: String = java.util.UUID.randomUUID().toString(),

    // 💡 优化 2：保持在主构造函数中，确保 .copy() 完美生效
    var title: String = "",

    var rootNode: MindMapNode = MindMapNode(text = "中心主题")
) : UIBlock()

@Composable
fun InteractiveTableBlock(
    tableData: TableData,
    onUpdate: (TableData) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 🌟 1. 读取全局设置的自定义笔记字体颜色
    val context = LocalContext.current
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
    }

    var showMenu by remember { mutableStateOf(false) }

    // 【大厂级流式重构】：引入标准化栅格宽度 (130.dp)
    val defaultColumnWidth = 130.dp

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        // 表格专属标题输入区
        var titleState by remember {
            mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(tableData.title))
        }

        LaunchedEffect(tableData.title) {
            if (titleState.text != tableData.title) {
                titleState = androidx.compose.ui.text.input.TextFieldValue(
                    text = tableData.title,
                    selection = androidx.compose.ui.text.TextRange(tableData.title.length)
                )
            }
        }

        BasicTextField(
            value = titleState,
            onValueChange = { newValue ->
                titleState = newValue
                if (newValue.text != tableData.title) onUpdate(tableData.copy(title = newValue.text))
            },
            textStyle = androidx.compose.ui.text.TextStyle(
                color = customTextColor, // 🌟 2. 修改表格标题字体颜色
                fontSize = 15.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    if (titleState.text.isEmpty()) {
                        Text("（ 添加标题 ）", color = customTextColor.copy(alpha = 0.5f), fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    innerTextField()
                }
            }
        )

        // 表格上方两条小横线手柄
        Box(contentAlignment = Alignment.Center) {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.height(24.dp).width(48.dp)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.DragHandle,
                    contentDescription = "表格选项",
                    tint = customTextColor // 🌟 3. 修改小横线手柄颜色
                )
            }

            MaterialTheme(shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(12.dp))) {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(text = { Text("向下添加一行") }, onClick = {
                        val newRow = List(tableData.cols) { "" }
                        onUpdate(tableData.copy(rows = tableData.rows + 1, cells = tableData.cells + listOf(newRow)))
                        showMenu = false
                    })
                    DropdownMenuItem(text = { Text("向右添加一列") }, onClick = {
                        val newCells = tableData.cells.map { it + "" }
                        onUpdate(tableData.copy(cols = tableData.cols + 1, cells = newCells))
                        showMenu = false
                    })
                    Divider(modifier = Modifier.padding(horizontal = 8.dp))
                    DropdownMenuItem(text = { Text("删除底部行", color = MaterialTheme.colorScheme.error) }, onClick = {
                        if (tableData.rows > 1) onUpdate(tableData.copy(rows = tableData.rows - 1, cells = tableData.cells.dropLast(1))) else onDelete()
                        showMenu = false
                    })
                    DropdownMenuItem(text = { Text("删除右侧列", color = MaterialTheme.colorScheme.error) }, onClick = {
                        if (tableData.cols > 1) onUpdate(tableData.copy(cols = tableData.cols - 1, cells = tableData.cells.map { it.dropLast(1) })) else onDelete()
                        showMenu = false
                    })
                    DropdownMenuItem(text = { Text("删除整个表格", color = MaterialTheme.colorScheme.error) }, onClick = {
                        showMenu = false; onDelete()
                    })
                }
            }
        }

        Box(
            modifier = Modifier
                .wrapContentWidth()
                .horizontalScroll(rememberScrollState())
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
        ){
            Column(modifier = Modifier.wrapContentWidth()) {
                tableData.cells.forEachIndexed { r, row ->
                    if (r < tableData.rows) {
                        Row(
                            modifier = Modifier
                                .wrapContentWidth()
                                .height(IntrinsicSize.Max)
                        ) {
                            row.forEachIndexed { c, cellText ->
                                if (c < tableData.cols) {
                                    Box(
                                        modifier = Modifier
                                            .width(defaultColumnWidth)
                                            .fillMaxHeight()
                                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        var textState by remember(tableData.id, r, c) {
                                            mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(cellText))
                                        }

                                        LaunchedEffect(cellText) {
                                            if (textState.text != cellText) {
                                                textState = androidx.compose.ui.text.input.TextFieldValue(
                                                    text = cellText,
                                                    selection = androidx.compose.ui.text.TextRange(cellText.length)
                                                )
                                            }
                                        }

                                        BasicTextField(
                                            value = textState,
                                            onValueChange = { newValue ->
                                                textState = newValue
                                                if (newValue.text != cellText) {
                                                    val newCells = tableData.cells.mapIndexed { i, rList ->
                                                        if (i == r) rList.mapIndexed { j, oldText -> if (j == c) newValue.text else oldText } else rList
                                                    }
                                                    onUpdate(tableData.copy(cells = newCells))
                                                }
                                            },
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                color = customTextColor, // 🌟 4. 修改表格单元格内容字体颜色
                                                fontSize = 14.sp,
                                                textAlign = TextAlign.Center
                                            )
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
}

// ================= 2. 交互式思维导图组件 =================
@Composable
fun InteractiveMindMapBlock(
    title: String, // 接收并绑定当前区块的专属标题
    rootNode: MindMapNode,
    selectedNodeId: String?,
    onTitleChange: (String) -> Unit, // 标题变更回调
    onNodeSelect: (String?) -> Unit,
    onUpdate: (MindMapNode) -> Unit,
    onDeleteMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 🌟 1. 声明上下文与主题状态（读取自定义笔记字体颜色）
    val context = LocalContext.current
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
    }

    // 🌟 2. 声明菜单显示控制状态
    var showMapMenu by remember { mutableStateOf(false) }

    // 升级为 Column 布局，以便将标题输入框渲染在图表正上方
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        // 🌟 1. 读取全局设置的自定义笔记字体颜色
        val context = androidx.compose.ui.platform.LocalContext.current
        val themeManager = remember { ThemeSettingsManager(context) }
        val themeState by themeManager.themeStateFlow.collectAsState(initial = AppThemeState())
        // 1. 判断是否处于深色模式
        val isDarkTheme = when (themeState.themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        }

// 2. 🌟 核心修复：必须加 .toInt()，并判断如果深色模式下是纯黑，就强制转为纯白
        val parsedColor = androidx.compose.ui.graphics.Color(themeState.noteTextColorHex.toInt())

        val customTextColor = if (isDarkTheme && parsedColor == androidx.compose.ui.graphics.Color.Black) {
            androidx.compose.ui.graphics.Color.White
        } else {
            parsedColor
        }

        // ==================================================================
        // 💡 【大厂级状态隔离】：彻底移除动态键，防止中文拼音输入中途被强杀
        // ==================================================================
        var titleState by remember {
            mutableStateOf(
                androidx.compose.ui.text.input.TextFieldValue(
                    text = title,
                    // 初始化时，将光标安全地置于文本最末尾
                    selection = androidx.compose.ui.text.TextRange(title.length)
                )
            )
        }

        // ==================================================================
        // 🛡️ 【防崩溃核心修复】：时光机/外部加载时的柔性同步与光标越界保护
        // ==================================================================
        LaunchedEffect(title) {
            if (titleState.text != title) {
                // ⚠️ 绝不能只 copy text！必须同步重置光标到新文本末尾，彻底杜绝越界闪退！
                titleState = titleState.copy(
                    text = title,
                    selection = androidx.compose.ui.text.TextRange(title.length)
                )
            }
        }

        // ==================================================================
        // 🎨 【UI 渲染层】：极简拦截，精准派发
        // ==================================================================
        androidx.compose.foundation.text.BasicTextField(
            value = titleState,
            onValueChange = { newValue ->
                titleState = newValue
                // 精准拦截：只有真实文本发生改变时，才向外层派发持久化指令，避免光标移动引发无效重组
                if (newValue.text != title) {
                    onTitleChange(newValue.text)
                }
            },
            textStyle = androidx.compose.ui.text.TextStyle(
                color = customTextColor, // 🌟 2. 将思维导图标题修改为自定义笔记字体颜色
                fontSize = 15.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            ),
            decorationBox = { innerTextField ->
                androidx.compose.foundation.layout.Box(
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    // 当标题为空时，高雅地显示占位符提示，使用 alpha 调节透明度打造悬浮质感
                    if (titleState.text.isEmpty()) {
                        androidx.compose.material3.Text(
                            text = "（ 添加标题 ）",
                            color = customTextColor.copy(alpha = 0.5f), // 🌟 3. 占位符也同步应用自定义颜色
                            fontSize = 15.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                    innerTextField()
                }
            }
        )
    }

        // ==========================================
        // 下面紧接着保留您的思维导图 Canvas 画布渲染代码
        // ...

        // 思维导图本体画布，横向过长时支持水平滑动
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(16.dp)
                .pointerInput(Unit) {
                    // 点击导图空白处清空选中节点，长按呼出删除整图菜单
                    detectTapGestures(
                        onTap = { onNodeSelect(null) },
                        onLongPress = { showMapMenu = true }
                    )
                }
        ) {
            MindMapNodeView(
                node = rootNode,
                isRoot = true,
                selectedNodeId = selectedNodeId,
                onNodeSelect = onNodeSelect,
                onTextChange = { id, text -> onUpdate(updateNodeText(rootNode, id, text)) }
            )

            // 大厂级高颜圆角菜单
            MaterialTheme(shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(12.dp))) {
                DropdownMenu(
                    expanded = showMapMenu,
                    onDismissRequest = { showMapMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text("删除整个思维导图", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMapMenu = false
                            onDeleteMap()
                        }
                    )
                }
            }
        }
    }




@Composable
private fun MindMapNodeView(
    node: MindMapNode,
    isRoot: Boolean,
    selectedNodeId: String?,
    onNodeSelect: (String) -> Unit,
    onTextChange: (String, String) -> Unit
) {
    val isSelected = node.id == selectedNodeId

    Row(verticalAlignment = Alignment.CenterVertically) {
        // 节点气泡
        Box(
            modifier = Modifier
                .padding(4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isRoot -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }
                )
                .clickable { onNodeSelect(node.id) }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            var localNodeText by remember(node.id) {
                mutableStateOf(TextFieldValue(node.text))
            }

            // 【隐患修复 1】：安全的光标越界保护机制
            LaunchedEffect(node.text) {
                if (localNodeText.text != node.text) {
                    // 当从底边栏修改文字同步过来时，必须重置光标位置，否则如果新文字比老文字短，光标越界会直接引发崩溃！
                    localNodeText = TextFieldValue(
                        text = node.text,
                        selection = TextRange(node.text.length)
                    )
                }
            }

            val textColor = when {
                isSelected -> MaterialTheme.colorScheme.onPrimary
                isRoot -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSecondaryContainer
            }

            BasicTextField(
                value = localNodeText,
                onValueChange = { newValue ->
                    localNodeText = newValue
                    if (newValue.text != node.text) {
                        onTextChange(node.id, newValue.text)
                    }
                },
                textStyle = TextStyle(color = textColor, fontSize = if (isRoot) 16.sp else 14.sp, textAlign = TextAlign.Center),
                modifier = Modifier
                    // 【隐患修复 2】：剔除极易引发测量无限循环/闪退的 IntrinsicSize.Min
                    .widthIn(min = 40.dp)
                    .onFocusChanged { focusState ->
                        // 【隐患修复 3】：防止焦点无限回调死循环
                        if (focusState.isFocused && !isSelected) {
                            onNodeSelect(node.id)
                        }
                    }
            )
        }

        // 递归渲染子分支与连接线
        if (node.children.isNotEmpty()) {
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.width(2.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Spacer(modifier = Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                node.children.forEach { child ->
                    // 【隐患修复 4】：必须为递归列表追加 key 属性！
                    key(child.id) {
                        MindMapNodeView(child, false, selectedNodeId, onNodeSelect, onTextChange)
                    }
                }
            }
        }
    }
}

// ================= 大厂级 Immutable 状态更新算法 =================
fun updateNodeText(root: MindMapNode, targetId: String, text: String): MindMapNode {
    if (root.id == targetId) return root.copy(text = text)
    return root.copy(children = root.children.map { updateNodeText(it, targetId, text) })
}

fun addMindMapChild(root: MindMapNode, targetId: String): MindMapNode {
    if (root.id == targetId) return root.copy(children = root.children + MindMapNode(text = "新分支"))
    return root.copy(children = root.children.map { addMindMapChild(it, targetId) })
}

fun addMindMapSibling(root: MindMapNode, targetId: String): MindMapNode {
    if (root.id == targetId) return root // 根节点不能添加并列分支
    val newChildren = mutableListOf<MindMapNode>()
    for (child in root.children) {
        newChildren.add(addMindMapSibling(child, targetId)) // 递归
        if (child.id == targetId) newChildren.add(MindMapNode(text = "新分支"))
    }
    return root.copy(children = newChildren)
}

fun deleteMindMapNode(root: MindMapNode, targetId: String): MindMapNode? {
    if (root.id == targetId) return null
    return root.copy(children = root.children.mapNotNull { deleteMindMapNode(it, targetId) })
}
// ... 之前的 updateNodeText, addMindMapChild 等算法保持不变 ...

// 【新增】：节点精确查找算法，用于在独立编辑台中显示当前节点的文字
fun findMindMapNode(root: MindMapNode, targetId: String): MindMapNode? {
    if (root.id == targetId) return root
    for (child in root.children) {
        val found = findMindMapNode(child, targetId)
        if (found != null) return found
    }
    return null
}

fun deepCopyMindMap(node: MindMapNode): MindMapNode {
    return node.copy(children = node.children.map { deepCopyMindMap(it) })
}