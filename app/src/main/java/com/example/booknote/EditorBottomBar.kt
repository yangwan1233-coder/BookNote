package com.example.booknote

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

// 纯粹的扩展功能按钮组件
@Composable
fun EditorBottomBar(
    onMindMapClick: () -> Unit,
    onTableClick: () -> Unit
) {
    // 思维导图按钮 (AccountTree 图标代表分支)
    IconButton(onClick = onMindMapClick) {
        Icon(
            imageVector = Icons.Default.AccountTree,
            contentDescription = "思维导图",
            tint = MaterialTheme.colorScheme.primary
        )
    }

    // 表格按钮 (GridOn 图标代表表格)
    IconButton(onClick = onTableClick) {
        Icon(
            imageVector = Icons.Default.GridOn,
            contentDescription = "表格",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}