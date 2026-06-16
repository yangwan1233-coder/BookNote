package com.example.booknote

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 【第一驾马车】：数据表实体
 * @Entity 注解告诉 Room，在底层 SQLite 数据库中建一张名为 "todos" 的表。
 */
@Entity(tableName = "todos")
data class TodoEntity(
    // 将 id 设为主键 (PrimaryKey)，绝不允许重复
    @PrimaryKey
    val id: String,

    val text: String,

    // 0 代表 false (未完成)，1 代表 true (已完成) - Room 底层会自动转换
    val isCompleted: Boolean,

    val completedAt: Long?,

    // 新增：创建/修改时间戳，用于精准排序
    val timestamp: Long = System.currentTimeMillis()
)