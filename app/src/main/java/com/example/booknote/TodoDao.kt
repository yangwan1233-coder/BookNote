package com.example.booknote

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete

import kotlinx.coroutines.flow.Flow

/**
 * 【第二驾马车】：数据访问对象 (DAO)
 * 这里存放所有操作数据库的指令。
 */
@Dao
interface TodoDao {

    // ================= 响应式同步接口 =================

    // 使用 Flow 实时监听未完成的前 3 条待办（按时间戳倒序）
    @Query("SELECT * FROM todos WHERE isCompleted = 0 ORDER BY timestamp DESC LIMIT 3")
    fun observeTop3PendingTodos(): Flow<List<TodoEntity>>

    // ================= 桌面小部件专属的高高性能接口 =================

    // 高效查询：只找未完成的，且限制最多 3 条，按创建时间倒序（最新的在前）
    @Query("SELECT * FROM todos WHERE isCompleted = 0 ORDER BY timestamp DESC LIMIT 3")
    suspend fun getTop3PendingTodos(): List<TodoEntity>

    // 高效更新：只更新状态和时间，不拉取对象
    @Query("UPDATE todos SET isCompleted = 1, completedAt = :completedTime WHERE id = :id")
    suspend fun markTodoAsCompleted(id: String, completedTime: Long)


    // ================= App 主界面 (TodoScreen) 需要的常规接口 =================

    // 获取所有待办数据，按时间倒序
    @Query("SELECT * FROM todos ORDER BY timestamp DESC")
    suspend fun getAllTodos(): List<TodoEntity>

    // 插入一条新待办（如果 ID 冲突则替换）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: TodoEntity)

    // 插入多条数据（用于初次数据迁移或批量保存）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(todos: List<TodoEntity>)

    // 删除一条待办
    @Delete
    suspend fun deleteTodo(todo: TodoEntity)
}