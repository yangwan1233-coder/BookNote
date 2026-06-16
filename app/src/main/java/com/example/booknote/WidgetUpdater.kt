package com.example.booknote

import android.content.Context

// ================= 全局小部件刷新中心 =================
object WidgetUpdater {

    /**
     * 在 App 任意地方（增删改查后）调用此方法，即可无感刷新桌面部件
     * 内部已封装异步任务调度。
     */
    fun forceUpdate(context: Context) {
        // 大厂高级实践：通过 WorkManager 异步分发刷新指令
        // 1. 确保任务在进程不稳定的情况下也能完成。
        // 2. 自动合并极短时间内的多次刷新，避免 UI 抖动和耗电。
        TodoSyncWorker.enqueue(context)
    }

    /**
     * 通知数据已更改
     */
    fun notifyDataChanged(context: Context) {
        forceUpdate(context)
    }
}
