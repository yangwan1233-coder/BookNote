package com.example.booknote

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * 核心导出引擎：将 Compose 截取的长图 Bitmap 存入 Android 10+ 的公共图库
 * @return 成功返回 true，失败返回 false
 */
suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap, noteTitle: String): Boolean {
    return withContext(Dispatchers.IO) {
        val safeTitle = if (noteTitle.isBlank()) "BookNote" else noteTitle.take(15)
        val filename = "${safeTitle}_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null
        var isSuccess = false

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BookNote")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                fos = resolver.openOutputStream(uri)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos!!)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                isSuccess = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isSuccess = false
        } finally {
            fos?.close()
        }
        return@withContext isSuccess // 👈 返回状态给主界面统一处理
    }
}