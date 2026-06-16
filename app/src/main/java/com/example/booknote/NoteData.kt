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
                // 【防越界优化】：严格校验数组长度，防止脏数据导致越界崩溃
                imagePaths = if (parts.size >= 6 && parts[5].isNotBlank()) parts[5].split(",") else emptyList(),
                isArchived = if (parts.size >= 7) parts[6].toBooleanStrictOrNull() ?: false else false,
                isDeleted = if (parts.size >= 8) parts[7].toBooleanStrictOrNull() ?: false else false
            )
        } else null
    }
}

const val DATA_FILE_NAME = "booknote_system_data.txt"

fun getSystemStorageUri(context: Context): String? = context.getSharedPreferences("booknote_prefs", Context.MODE_PRIVATE).getString("storage_uri", null)

suspend fun saveNotesToDisk(context: Context, notes: List<Note>) = withContext(Dispatchers.IO) {
    val uriStr = getSystemStorageUri(context)
    val data = serializeNotes(notes)
    try {
        if (!uriStr.isNullOrEmpty()) {
            val folder = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
            var file = folder?.findFile(DATA_FILE_NAME)
            if (file == null) file = folder?.createFile("text/plain", DATA_FILE_NAME)

            // 【流安全优化】：使用 .use 确保写入完毕后自动释放内存句柄
            file?.uri?.let { uri ->
                context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                    os.write(data.toByteArray())
                }
            }
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
            if (file != null) {
                // 【内存泄漏修复】：严格使用 .use 包裹底层 InputStream
                return context.contentResolver.openInputStream(file.uri)?.use { ins ->
                    val text = ins.bufferedReader().use { it.readText() }
                    deserializeNotes(text)
                } ?: emptyList()
            }
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
                zos.write(serializeNotes(notes).toByteArray())
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