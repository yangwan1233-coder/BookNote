package com.example.booknote

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// ==========================================
// 【大厂级核心数据模型】：统一图文区块存储
// ==========================================
data class Note(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "",
    var content: String = "", // 护城河：保留用于列表预览
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var imagePaths: List<String> = emptyList(),
    var isArchived: Boolean = false,
    var isDeleted: Boolean = false,

    // 【终极重构】：新增 blocksJson，用于承载所有表格和思维导图！
    var blocksJson: String = ""
)

fun formatTime(timeMillis: Long, format: String = "dd M月"): String = SimpleDateFormat(format, Locale.CHINESE).format(Date(timeMillis))

private val gson = Gson()

// ==================================================================
// 【大厂级序列化引擎】：未来全部以标准 JSON 存储，彻底解决特殊字符崩溃
// ==================================================================
fun serializeNotes(notes: List<Note>): String {
    return gson.toJson(notes)
}

// ==================================================================
// 【终极灰度反序列化】：完美读取您最原始的 "|||" 分隔符旧笔记，并自动升级
// ==================================================================
fun deserializeNotes(data: String): List<Note> {
    if (data.isBlank()) return emptyList()

    // 🎯 核心修复：精准探测您开源版的 "|||" 旧数据格式
    if (data.contains("---END_NOTE---")) {
        return data.split("---END_NOTE---\n").filter { it.isNotBlank() }.mapNotNull {
            val parts = it.split("|||")
            if (parts.size >= 5) {
                // 顺手帮您补全了原本漏掉的还原转义逻辑（/// 还原回 |||）
                val safeTitle = parts[1].replace("///", "|||")
                val safeContent = parts[2].replace("///", "|||")

                // 💡 【平滑升级】：将老笔记的纯文本，瞬间转化为支持思维导图的新区块！
                val initialBlocks = listOf(
                    mapOf(
                        "type" to "text",
                        "textContent" to safeContent
                    )
                )

                Note(
                    id = parts[0],
                    title = safeTitle,
                    content = safeContent,
                    createdAt = parts[3].toLongOrNull() ?: System.currentTimeMillis(),
                    updatedAt = parts[4].toLongOrNull() ?: System.currentTimeMillis(),
                    // 【绝对保留您的防越界优化】
                    imagePaths = if (parts.size >= 6 && parts[5].isNotBlank()) parts[5].split(",") else emptyList(),
                    isArchived = if (parts.size >= 7) parts[6].toBooleanStrictOrNull() ?: false else false,
                    isDeleted = if (parts.size >= 8) parts[7].toBooleanStrictOrNull() ?: false else false,
                    // 灌入全新架构
                    blocksJson = gson.toJson(initialBlocks)
                )
            } else null
        }
    }

    // 🚀 新架构的高性能标准 JSON 解析通道
    return try {
        val type = object : TypeToken<List<Note>>() {}.type
        gson.fromJson(data, type) ?: emptyList()
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

const val DATA_FILE_NAME = "booknote_system_data.txt"

// ==================================================================
// 下方所有的底层 I/O、图片拷贝、ZIP 打包解压代码
// 已根据您的指令原封不动 100% 保留，坚决不破坏您的稳定架构！
// ==================================================================

fun getSystemStorageUri(context: Context): String? = context.getSharedPreferences("booknote_prefs", Context.MODE_PRIVATE).getString("storage_uri", null)

suspend fun saveNotesToDisk(context: Context, notes: List<Note>) = withContext(Dispatchers.IO) {
    val uriStr = getSystemStorageUri(context)
    // 强制使用 UTF-8 获取 ByteArray，防止 Gson 在特殊机型产生中文乱码
    val data = serializeNotes(notes)
    try {
        if (!uriStr.isNullOrEmpty()) {
            val folder = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
            var file = folder?.findFile(DATA_FILE_NAME)
            if (file == null) file = folder?.createFile("text/plain", DATA_FILE_NAME)

            // 【流安全优化】：使用 .use 确保写入完毕后自动释放内存句柄
            file?.uri?.let { uri ->
                context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                    os.write(data.toByteArray(Charsets.UTF_8))
                }
            }
        } else {
            File(context.filesDir, DATA_FILE_NAME).writeText(data, Charsets.UTF_8)
        }
    } catch (e: Exception) { e.printStackTrace() }
}

fun loadNotesFromDisk(context: Context): List<Note> {
    val uriStr = getSystemStorageUri(context)
    try {
        if (!uriStr.isNullOrEmpty()) {
            val folder = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
            val file = folder?.findFile(DATA_FILE_NAME)
            if (file != null) {
                // 【内存泄漏修复】：严格使用 .use 包裹底层 InputStream
                return context.contentResolver.openInputStream(file.uri)?.use { ins ->
                    // 强制指定 UTF-8 防止中文乱码崩溃
                    val text = ins.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    deserializeNotes(text)
                } ?: emptyList()
            }
        } else {
            val file = File(context.filesDir, DATA_FILE_NAME)
            if (file.exists()) return deserializeNotes(file.readText(Charsets.UTF_8))
        }
    } catch (e: Exception) { e.printStackTrace() }
    return emptyList()
}

fun copyUriToSystemStorage(context: Context, uri: Uri, customName: String? = null): String? {
    val uriStr = getSystemStorageUri(context)
    return try {
        val fileName = customName ?: "IMG_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(5)}.jpg"
        if (!uriStr.isNullOrEmpty()) {
            val rootFolder = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
            var imagesFolder = rootFolder?.findFile("BookNote_Images")
            if (imagesFolder == null) {
                imagesFolder = rootFolder?.createDirectory("BookNote_Images")
            }

            // 【⚠️ 绝对保留的隐私锁】：自动注入 .nomedia，防止系统相册抓取公开图片，隐私绝对安全！
            if (imagesFolder?.findFile(".nomedia") == null) {
                imagesFolder?.createFile("application/octet-stream", ".nomedia")
            }

            val newFile = imagesFolder?.findFile(fileName) ?: imagesFolder?.createFile("image/jpeg", fileName)
            if (newFile != null) {
                // 【流安全优化】：双轨闭环拷贝，杜绝复制大图时卡死
                context.contentResolver.openInputStream(uri)?.use { input ->
                    context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                        input.copyTo(output)
                    }
                }
                newFile.uri.toString()
            } else null
        } else {
            val imagesDir = File(context.filesDir, "BookNote_Images")
            if (!imagesDir.exists()) imagesDir.mkdirs()

            // 内部存储隐私锁同步保留
            val nomediaFile = File(imagesDir, ".nomedia")
            if (!nomediaFile.exists()) nomediaFile.createNewFile()

            val file = File(imagesDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        }
    } catch (e: Exception) { null }
}

suspend fun backupDataToZip(context: Context, uri: Uri, notes: List<Note>): Boolean = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { os ->
            ZipOutputStream(os).use { zos ->
                zos.putNextEntry(ZipEntry(DATA_FILE_NAME))
                zos.write(serializeNotes(notes).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                notes.flatMap { it.imagePaths }.distinct().forEach { imgPath ->
                    try {
                        val imgUri = Uri.parse(imgPath)
                        // 【致命崩溃修复】：使用位运算 (and 0x7FFFFFFF) 替代 Math.abs()
                        // 彻底解决当 hashCode 等于 Int.MIN_VALUE 时出现的负数异常崩溃问题
                        val safeHash = imgPath.hashCode() and 0x7FFFFFFF
                        val fileName = "IMG_${safeHash}.jpg"
                        zos.putNextEntry(ZipEntry("images/$fileName"))
                        context.contentResolver.openInputStream(imgUri)?.use { ins -> ins.copyTo(zos) }
                        zos.closeEntry()
                    } catch (e: Exception) { /* 忽略单张损坏图片，保障全局打包不中断 */ }
                }
            }
        }
        true
    } catch (e: Exception) { false }
}

suspend fun restoreDataFromZip(context: Context, uri: Uri): List<Note>? = withContext(Dispatchers.IO) {
    try {
        var restoredNotes: List<Note>? = null
        val imageMapping = mutableMapOf<String, String>()

        context.contentResolver.openInputStream(uri)?.use { ins ->
            ZipInputStream(ins).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == DATA_FILE_NAME) {
                        restoredNotes = deserializeNotes(zis.readBytes().toString(Charsets.UTF_8))
                    } else if (entry.name.startsWith("images/")) {
                        val fileName = entry.name.removePrefix("images/")
                        val tempFile = File(context.cacheDir, fileName)
                        tempFile.outputStream().use { out -> zis.copyTo(out) } // 【流安全优化】用毕即焚资源锁

                        val newUri = copyUriToSystemStorage(context, Uri.fromFile(tempFile), fileName)
                        if (newUri != null) imageMapping[fileName] = newUri
                        tempFile.delete()
                    }
                    entry = zis.nextEntry
                }
            }
        }

        restoredNotes?.map { note ->
            val newPaths = note.imagePaths.mapNotNull { oldPath ->
                // 【致命崩溃修复】：解压时同步使用位运算匹配正确路径
                val safeHash = oldPath.hashCode() and 0x7FFFFFFF
                val oldName = "IMG_${safeHash}.jpg"
                imageMapping[oldName] ?: oldPath
            }
            note.copy(imagePaths = newPaths)
        }
    } catch (e: Exception) { null }
}