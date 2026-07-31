package com.example.booknote

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.text.input.TextFieldValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// 专门用于存盘的中间件模型，完美避开 Compose 状态类的序列化崩溃
data class BlockSaveModel(
    val type: String, // 类型标识："text", "table", "mindmap"
    val textContent: String = "",
    val title: String = "",
    val tableData: TableData? = null,
    val mindMapRoot: MindMapNode? = null
)

object BlockSerializer {
    private val gson = Gson()

    // 1. 将界面上的 blocks 打包成 String 存盘
    fun serializeBlocks(blocks: List<UIBlock>): String {
        val saveList = blocks.map { block ->
            when (block) {
                is UITextBlock -> BlockSaveModel(type = "text", textContent = block.content.text)
                is UITableBlock -> BlockSaveModel(type = "table", tableData = block.tableData)
                is UIMindMapBlock -> BlockSaveModel(
                    type = "mindmap",
                    // 💡 【核心修复 1】：打包存盘时，必须把 block.title 塞进模型里！绝不能把它落下！
                    title = block.title,
                    mindMapRoot = block.rootNode
                )
            }
        }
        return gson.toJson(saveList)
    }

    // 2. 从数据库读取 String 恢复为界面上的 blocks
    fun deserializeBlocks(json: String): SnapshotStateList<UIBlock> {
        if (json.isBlank()) return mutableListOf<UIBlock>().toMutableStateList()

        return try {
            val type = object : TypeToken<List<BlockSaveModel>>() {}.type
            val saveList: List<BlockSaveModel> = gson.fromJson(json, type)

            saveList.mapNotNull { model ->
                when (model.type) {
                    // 🛡️ 【大厂防崩溃优化】：加上 ?: "" 兜底，防止读取旧数据时因 null 导致整个列表白屏崩溃
                    "text" -> UITextBlock(androidx.compose.ui.text.input.TextFieldValue(model.textContent ?: ""))
                    "table" -> model.tableData?.let { UITableBlock(it) }
                    "mindmap" -> model.mindMapRoot?.let {
                        UIMindMapBlock(
                            // 🛡️ 【核心修复 2】：读取时同样加上 ?: "" 防御性校验
                            title = model.title ?: "",
                            rootNode = it
                        )
                    }

                    else -> null
                }
            }.toMutableStateList()
        } catch (e: Exception) {
            e.printStackTrace()
            mutableListOf<UIBlock>().toMutableStateList()
        }
    }
}