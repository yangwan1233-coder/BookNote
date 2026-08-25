package com.example.booknote

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 🌟 核心状态收编：全局唯一的笔记数据源 (Single Source of Truth)
 * 继承 AndroidViewModel 以便安全地获取 Application Context 进行本地读写
 */
class NoteViewModel(application: Application) : AndroidViewModel(application) {

    // 内部可变的 StateFlow，专门在后台安全修改，绝不暴露给 UI 直接篡改
    private val _notesState = MutableStateFlow<List<Note>>(emptyList())

    // 对外暴露的只读 StateFlow，UI 层只能订阅它来刷新界面
    val notesState: StateFlow<List<Note>> = _notesState.asStateFlow()

    init {
        // ViewModel 诞生时，立即去磁盘拉取数据
        loadNotes()
    }

    /**
     * 1. 异步加载数据与灰度升级指南 (从原来的 MainActivity 中剥离出来)
     */
    private fun loadNotes() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val localNotes = loadNotesFromDisk(context)
            val prefs = context.getSharedPreferences("booknote_prefs", Context.MODE_PRIVATE)

            // 💡 升级控制中心：当前指南版本
            val currentGuideVersion = 9
            val savedGuideVersion = prefs.getInt("guide_version", 0)

            val finalNotes = mutableListOf<Note>()
            val currentTime = System.currentTimeMillis()

            if (savedGuideVersion < currentGuideVersion) {
                // 严格过滤掉以前各版本的旧指南，只保留用户的真实心血笔记
                val userRealNotes = localNotes.filterNot { it.title.contains("BookNote 极速上手指南") }

                // 构建大厂级【三位一体】超级使用指南
                val guideBlocks = mutableListOf<UIBlock>()
                val demoImages = listOf(
                    "android.resource://${context.packageName}/${R.drawable.guide_pic_1}",
                    "android.resource://${context.packageName}/${R.drawable.guide_pic_2}",
                    "android.resource://${context.packageName}/${R.drawable.guide_pic_3}"
                )

                val introText = """
                    欢迎使用 BookNote 智能备忘录！
                    （不记录来时路，何谈不负当下？）
                    
                    最新更新内容：全局自定义背景和笔记字体颜色，笔记预览卡片滑动动画。
                    本应用已全面升级为大厂级“流式图文排版架构”。在这里，文字、表格与思维导图不再是孤立的组件，而是可以随心所欲自由混排的生命体！
                """.trimIndent()
                guideBlocks.add(UITextBlock(androidx.compose.ui.text.input.TextFieldValue(introText)))

                val tableBlock = UITableBlock(
                    tableData = TableData(
                        title = "BookNote 核心功能表",
                        rows = 5, cols = 3,
                        cells = listOf(
                            listOf("核心功能", "操作手势", "大厂级稳定性保障"),
                            listOf("思维导图", "底部工具栏一键插入", "节点无限延伸，防越界崩溃"),
                            listOf("智能表格", "点击插入，行列自由增删", "双轨闭环拷贝，杜绝大文件卡死"),
                            listOf("九宫格预览", "底部工具栏一键插入", "矩阵零卡顿，渲染极丝滑！"),
                            listOf("数据安全", "防杀进程自动存盘", "生命周期感知，锁死 UTF-8 防乱码")
                        )
                    )
                )
                guideBlocks.add(tableBlock)

                val midText = "下面为您动态演示本软件的模块化思维导图架构，您可以点击任意节点直接进行向右延伸和编辑："
                guideBlocks.add(UITextBlock(androidx.compose.ui.text.input.TextFieldValue(midText)))

                val mindMapBlock = UIMindMapBlock(
                    rootNode = MindMapNode(
                        text = "BookNote 核心宇宙",
                        children = listOf(
                            MindMapNode(
                                text = "⚡ 模块化排版（已打通）",
                                children = listOf(MindMapNode(text = "流式正文块"), MindMapNode(text = "动态数据表"))
                            ),
                            MindMapNode(text = "💾 坚固持久化（已锁定）"),
                            MindMapNode(text = "🛡️ 永久图片锁（已护航）")
                        )
                    )
                )
                guideBlocks.add(mindMapBlock)

                val visualAndEditBlock = UITextBlock(androidx.compose.ui.text.input.TextFieldValue("""
                    【🎨 视觉与顶级动画操作】
                    • 沉浸视觉：全面穿透状态栏，笔记按年份自动生成悬浮气泡精美分组。
                    • 物理级滑动：直接在主页对本条卡片【向左滑动】，归档与删除按钮将与卡片同频丝滑滑出，带物理阻尼回弹，极致解压！
                    
                    【📝 沉浸式图文编辑】
                    • 智能防呆：离开页面实时无感自动保存；空白文档自动无痕销毁。
                    • 时光倒流：底部的胶囊工具栏支持【撤销】与【重做】双向历史栈，打错字随时反悔。
                    • 精准排版：长按滑动选中任意几行文本，点击【列表图标】精准追加圆点 • 符号。还支持一键在光标处插入 ()。
                    • 顶级画廊：支持插入多达 9 张图片，点击进入全屏画廊左右滑动，支持唤醒系统级裁剪与修图，长按快捷删除。
                """.trimIndent()))
                guideBlocks.add(visualAndEditBlock)

                val widgetBlock = UITextBlock(androidx.compose.ui.text.input.TextFieldValue("""
                    【🎯 极速待办与桌面魔法 (New!)】
                    • 桌面小部件：长按手机桌面即可添加 BookNote 专属 2x2 待办部件，实现亚毫秒级双向同步！
                    • ⚠️ 使用提醒：添加前请务必在系统设置中允许【添加桌面快捷方式】。首次添加后，请在 App 内随便完成两条待办，部件即可唤醒同步！（显示效果持续暴走优化中...）
                """.trimIndent()))
                guideBlocks.add(widgetBlock)

                val advanceMindMapBlock = UIMindMapBlock(
                    rootNode = MindMapNode(
                        text = "🌟 高阶玩法探索",
                        children = listOf(
                            MindMapNode(
                                text = "🗂️ 极客级数据与隐私",
                                children = listOf(
                                    MindMapNode(text = "0 内存占用 (系统盘直存)"),
                                    MindMapNode(text = ".nomedia 黑科技 (防图库污染)"),
                                    MindMapNode(text = "聚合备份舱 (ZIP丝滑克隆)")
                                )
                            ),
                            MindMapNode(
                                text = "⚙️ 莫奈级个性化主题",
                                children = listOf(
                                    MindMapNode(text = "5 款高级莫奈印象派预设"),
                                    MindMapNode(text = "RGB 双滑轮深度定制"),
                                    MindMapNode(text = "系统壁纸动态取色")
                                )
                            )
                        )
                    )
                )
                guideBlocks.add(advanceMindMapBlock)

                val contactBlock = UITextBlock(androidx.compose.ui.text.input.TextFieldValue("""
                    📮 开发者与开源社区
                    • 邮箱：3363099285@qq.com（反馈与建议请联系）
                    • 酷安Id：Yangwan1233
                    • GitHub官方仓库：https://github.com/yangwan1233-coder/BookNote
                """.trimIndent()))
                guideBlocks.add(contactBlock)

                val footerText = "后续版本将持续为您爆肝更新，感谢您的陪伴与信任！祝您记录愉快！"
                guideBlocks.add(UITextBlock(androidx.compose.ui.text.input.TextFieldValue(footerText)))

                val welcomeNote = Note(
                    title = "💡 BookNote 极速上手指南 (阅读后可左滑删除)",
                    content = introText,
                    imagePaths = demoImages,
                    blocksJson = BlockSerializer.serializeBlocks(guideBlocks),
                    createdAt = currentTime,
                    updatedAt = currentTime
                )

                finalNotes.add(welcomeNote)
                finalNotes.addAll(userRealNotes)

                // 强制安全同步落盘，并刷新版本号
                saveNotesToDisk(context, finalNotes)
                prefs.edit().putInt("guide_version", currentGuideVersion).apply()

            } else {
                // 版本号未变，直接读取本地数据
                finalNotes.addAll(localNotes)
            }

            // 🌟 极其优雅：通过 MutableStateFlow 发送最新状态，UI 瞬间自动刷新！
            _notesState.value = finalNotes
        }
    }

    /**
     * 2. 新增或更新笔记 (供 EditNoteScreen 退出时调用)
     */
    fun saveNote(updatedNote: Note) {
        val currentList = _notesState.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == updatedNote.id }

        if (index >= 0) {
            currentList[index] = updatedNote
        } else {
            currentList.add(updatedNote)
        }

        _notesState.value = currentList
        syncToDisk(currentList)
    }

    /**
     * 3. 批量更新笔记（供归档、回收站恢复等操作调用）
     */
    fun updateNotesList(newList: List<Note>) {
        _notesState.value = newList
        syncToDisk(newList)
    }

    /**
     * 4. 删除单条笔记（供丢弃空白文档时调用）
     */
    fun removeNoteById(noteId: String) {
        val currentList = _notesState.value.filterNot { it.id == noteId }
        _notesState.value = currentList
        syncToDisk(currentList)
    }

    /**
     * 🚀 终极后台落盘引擎（直接接收不可变快照，彻底消灭 ConcurrentModificationException）
     */
    private fun syncToDisk(listToSave: List<Note>) {
        viewModelScope.launch(Dispatchers.IO) {
            saveNotesToDisk(getApplication<Application>().applicationContext, listToSave)
        }
    }
}