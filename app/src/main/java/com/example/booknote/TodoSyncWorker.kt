package com.example.booknote

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.work.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 【数据同步卫士】：WorkManager 异步任务
 * 核心升级：不再仅仅是“刷新指令”，而是“数据搬运工”。
 * 它负责从数据库读取最新数据并持久化到小部件的状态存储中。
 */
class TodoSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            
            // 1. 从数据库读取最新的前三条未完成事项
            val top3Todos = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).todoDao()
                    .getTop3PendingTodos()
                    .map { WidgetTodoItem(id = it.id, content = it.text) }
            }

            val json = Gson().toJson(top3Todos)

            // 2. 更新所有小部件实例的状态（持久化存储）
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(TodoWidget::class.java)
            
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[TodoWidget.DATA_KEY] = json
                }
            }

            // 3. 通知系统 UI 立即重绘
            TodoWidget().updateAll(context)
            
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "todo_widget_sync_work"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<TodoSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
