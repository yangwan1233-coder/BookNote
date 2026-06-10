package com.example.booknote

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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

data class Note(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "",
    var content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var imagePaths: List<String> = emptyList(),
    var isArchived: Boolean = false,
    var isDeleted: Boolean = false
)

fun formatTime(timeMillis: Long, format: String = "dd M月"): String = SimpleDateFormat(format, Locale.CHINESE).format(Date(timeMillis))

// 序列化：将特殊字符替换，防止文本中自带分隔符导致解析崩溃
fun serializeNotes(notes: List<Note>): String {
    return notes.joinToString(separator = "---END_NOTE---\n") {
        val safeTitle = it.title.replace("|||", "///")
        val safeContent = it.content.replace("|||", "///")
        "${it.id}|||${safeTitle}|||${safeContent}|||${it.createdAt}|||${it.updatedAt}|||${it.imagePaths.joinToString(",")}|||${it.isArchived}|||${it.isDeleted}"
    }
}

fun deserializeNotes(data: String): List<Note> {
    if (data.isBlank()) return emptyList()
    return data.split("---END_NOTE---\n").filter { it.isNotBlank() }.mapNotNull {
        val parts = it.split("|||")
        if (parts.size >= 5) {
            Note(
                id = parts[0],
                title = parts[1],
                content = parts[2],
                createdAt = parts[3].toLongOrNull() ?: System.currentTimeMillis(),
                updatedAt = parts[4].toLongOrNull() ?: System.currentTimeMillis(),
                imagePaths = if (parts.size >= 6 && parts[5].isNotBlank()) parts[5].split(",") else emptyList(),
                isArchived = if (parts.size >= 7) parts[6].toBoolean() else false,
                isDeleted = if (parts.size >= 8) parts[7].toBoolean() else false
            )
        } else null
    }
}

const val DATA_FILE_NAME = "booknote_system_data.txt"

// 【核心修复1】将 Key 从 "system_storage_uri" 修正为 "storage_uri"，与 SettingsScreen 完全匹配！
fun getSystemStorageUri(context: Context): String? = context.getSharedPreferences("booknote_prefs", Context.MODE_PRIVATE).getString("storage_uri", null)

suspend fun saveNotesToDisk(context: Context, notes: List<Note>) = withContext(Dispatchers.IO) {
    val uriStr = getSystemStorageUri(context)
    val data = serializeNotes(notes)
    try {
        if (!uriStr.isNullOrEmpty()) {
            val folder = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
            var file = folder?.findFile(DATA_FILE_NAME)
            if (file == null) file = folder?.createFile("text/plain", DATA_FILE_NAME)
            file?.uri?.let { uri -> context.contentResolver.openOutputStream(uri, "wt")?.use { os -> os.write(data.toByteArray()) } }
        } else {
            File(context.filesDir, DATA_FILE_NAME).writeText(data)
        }
    } catch (e: Exception) { e.printStackTrace() }
}

fun loadNotesFromDisk(context: Context): List<Note> {
    val uriStr = getSystemStorageUri(context)
    try {
        if (!uriStr.isNullOrEmpty()) {
            val folder = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
            val file = folder?.findFile(DATA_FILE_NAME)
            if (file != null) return deserializeNotes(context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() } ?: "")
        } else {
            val file = File(context.filesDir, DATA_FILE_NAME)
            if (file.exists()) return deserializeNotes(file.readText())
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
            // 在外部系统存储中创建一个专门的 BookNote_Images 文件夹存放图片
            var imagesFolder = rootFolder?.findFile("BookNote_Images")
            if (imagesFolder == null) {
                imagesFolder = rootFolder?.createDirectory("BookNote_Images")
            }

            // 【核心隐身黑科技】：注入 .nomedia 文件！
            // 只要有这个文件，手机自带的相册、图库应用就会彻底无视这个文件夹，绝对不会把笔记图片暴露在相册里！
            if (imagesFolder?.findFile(".nomedia") == null) {
                imagesFolder?.createFile("application/octet-stream", ".nomedia")
            }

            val newFile = imagesFolder?.findFile(fileName) ?: imagesFolder?.createFile("image/jpeg", fileName)
            if (newFile != null) {
                context.contentResolver.openInputStream(uri)?.use { input -> context.contentResolver.openOutputStream(newFile.uri)?.use { output -> input.copyTo(output) } }
                newFile.uri.toString()
            } else null
        } else {
            val imagesDir = File(context.filesDir, "BookNote_Images")
            if (!imagesDir.exists()) imagesDir.mkdirs()

            // 内部存储也加一层保险
            val nomediaFile = File(imagesDir, ".nomedia")
            if (!nomediaFile.exists()) nomediaFile.createNewFile()

            val file = File(imagesDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            file.absolutePath
        }
    } catch (e: Exception) { null }
}

// 【核心修复3】深度备份：包含文本和所有引用的图片，使用安全的 hash 映射防止系统路径解析崩溃
suspend fun backupDataToZip(context: Context, uri: Uri, notes: List<Note>): Boolean = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { os ->
            ZipOutputStream(os).use { zos ->
                // 1. 备份文本数据
                zos.putNextEntry(ZipEntry(DATA_FILE_NAME))
                zos.write(serializeNotes(notes).toByteArray())
                zos.closeEntry()

                // 2. 备份所有引用的图片
                notes.flatMap { it.imagePaths }.distinct().forEach { imgPath ->
                    try {
                        val imgUri = Uri.parse(imgPath)
                        // 提取极其安全的文件名，不再依赖原始的复杂路径截取
                        val fileName = "IMG_${Math.abs(imgPath.hashCode())}.jpg"
                        zos.putNextEntry(ZipEntry("images/$fileName"))
                        context.contentResolver.openInputStream(imgUri)?.use { ins -> ins.copyTo(zos) }
                        zos.closeEntry()
                    } catch (e: Exception) { /* 忽略单张损坏的图片 */ }
                }
            }
        }
        true
    } catch (e: Exception) { false }
}

// 【核心修复4】深度恢复：解压并利用 hash 映射完美重构图片 URI
suspend fun restoreDataFromZip(context: Context, uri: Uri): List<Note>? = withContext(Dispatchers.IO) {
    try {
        var restoredNotes: List<Note>? = null
        val imageMapping = mutableMapOf<String, String>() // 记录：原图片名 -> 新生成的系统URI

        context.contentResolver.openInputStream(uri)?.use { ins ->
            ZipInputStream(ins).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == DATA_FILE_NAME) {
                        restoredNotes = deserializeNotes(zis.readBytes().toString(Charsets.UTF_8))
                    } else if (entry.name.startsWith("images/")) {
                        val fileName = entry.name.removePrefix("images/")
                        // 将图片写入当前系统存储，并记录新老路径映射
                        val tempFile = File(context.cacheDir, fileName)
                        tempFile.outputStream().use { zis.copyTo(it) }
                        val newUri = copyUriToSystemStorage(context, Uri.fromFile(tempFile), fileName)
                        if (newUri != null) imageMapping[fileName] = newUri
                        tempFile.delete()
                    }
                    entry = zis.nextEntry
                }
            }
        }

        // 映射更新：把恢复的笔记里的旧图片路径，替换成刚刚重构的新 URI
        restoredNotes?.map { note ->
            val newPaths = note.imagePaths.mapNotNull { oldPath ->
                val oldName = "IMG_${Math.abs(oldPath.hashCode())}.jpg"
                imageMapping[oldName] ?: oldPath
            }
            note.copy(imagePaths = newPaths)
        }
    } catch (e: Exception) { null }
}