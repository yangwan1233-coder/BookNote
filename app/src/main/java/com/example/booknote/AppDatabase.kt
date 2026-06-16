package com.example.booknote

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 【第三驾马车】：数据库大管家
 * 继承自 RoomDatabase，声明数据库包含哪些实体，以及当前的版本号。
 */
@Database(entities = [TodoEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // 暴露 DAO 供外部调用
    abstract fun todoDao(): TodoDao

    // 伴生对象，实现单例模式
    companion object {
        // @Volatile 保证多线程下的内存可见性（禁止指令重排序）
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // 获取数据库实例的唯一全局入口
        fun getInstance(context: Context): AppDatabase {
            // 如果 INSTANCE 不为空，直接返回；如果为空，则进入同步锁创建
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext, // 必须使用 applicationContext 防止内存泄漏
                    AppDatabase::class.java,
                    "booknote_secure_database" // 底层数据库文件的真实名称
                )
                    // ========================================================
                    // 【核心神仙优化】：破坏性迁移防崩机制
                    // 作用：当您以后修改了 TodoEntity（如增加、删除字段），
                    // 且忘了写复杂的数据库升级脚本时，Room 不会报错闪退，
                    // 而是会自动把旧表清空并按新结构重建！开发阶段绝对必备！
                    // ========================================================
                    .enableMultiInstanceInvalidation() // 允许跨进程失效通知，解决小部件进程数据滞后
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}