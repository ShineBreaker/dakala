package com.dakala.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.dakala.app.R
import com.dakala.app.data.org.OrgNote

/**
 * Org 待办列表组件
 *
 * 按完成状态分两组展示：未完成在前（保持文件内顺序），已完成在后。
 */
@Composable
fun TodoList(
    notes: List<OrgNote>,
    onToggle: (OrgNote) -> Unit,
    modifier: Modifier = Modifier
) {
    val incomplete = notes.filter { !it.isDone }
    val done = notes.filter { it.isDone }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (incomplete.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.incomplete_section),
                    count = incomplete.size
                )
            }
            items(incomplete, key = { it.lineIndex }) { note ->
                TodoCard(note = note, onToggle = { onToggle(note) })
            }
        }

        if (done.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = stringResource(R.string.completed_section),
                    count = done.size
                )
            }
            items(done, key = { it.lineIndex }) { note ->
                TodoCard(note = note, onToggle = { onToggle(note) })
            }
        }
    }
}

/**
 * 单个待办条目卡片
 */
@Composable
fun TodoCard(
    note: OrgNote,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (note.isDone) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp + ((note.level - 1).coerceAtLeast(0) * 12).dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 状态关键字
                    Text(
                        text = note.state,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (note.isDone) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    // 优先级
                    note.priority?.let { priority ->
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "#$priority",
                            style = MaterialTheme.typography.labelMedium,
                            color = when (priority) {
                                "A" -> MaterialTheme.colorScheme.error
                                "B" -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // 标题
                    Text(
                        text = note.title.ifEmpty { stringResource(R.string.todo_untitled) },
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (note.isDone) TextDecoration.LineThrough else null,
                        color = if (note.isDone) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // tags 与 planning 信息
                val meta = buildList {
                    if (note.tags.isNotEmpty()) add(note.tags.joinToString(" ") { ":$it:" })
                    note.scheduled?.let { add("S: " + formatOrgTimestamp(it)) }
                    note.deadline?.let { add("D: " + formatOrgTimestamp(it)) }
                    note.closed?.let { add("C: " + formatOrgTimestamp(it)) }
                }
                if (meta.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = meta.joinToString("  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 打钩按钮
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (note.isDone) {
                        Icons.Outlined.CheckCircle
                    } else {
                        Icons.Outlined.RadioButtonUnchecked
                    },
                    contentDescription = if (note.isDone) {
                        stringResource(R.string.todo_mark_undone)
                    } else {
                        stringResource(R.string.todo_mark_done)
                    },
                    tint = if (note.isDone) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * 添加待办对话框
 */
@Composable
fun AddTodoDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.todo_add_title)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.todo_title_label)) },
                placeholder = { Text(stringResource(R.string.todo_title_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim()) },
                enabled = title.isNotBlank()
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * 未选择 org 文件的空状态
 */
@Composable
fun TodoNoFileState(
    onPickFile: () -> Unit,
    onCreateFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.todo_no_file),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onPickFile) {
            Text(stringResource(R.string.todo_pick_file))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onCreateFile) {
            Text(stringResource(R.string.todo_create_file))
        }
    }
}

/**
 * 错误状态（含权限失效时重新选择文件入口）
 */
@Composable
fun TodoErrorState(
    message: String,
    showPickFile: Boolean,
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        if (showPickFile) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onPickFile) {
                Text(stringResource(R.string.todo_pick_file))
            }
        }
    }
}

/**
 * 将 org 时间戳原文转为简洁显示
 *
 * `<2026-10-20 Tue 14:30 +1w>` → `2026-10-20 14:30`
 * `[2026-10-20 Tue]` → `2026-10-20`
 */
private fun formatOrgTimestamp(raw: String): String {
    val inner = raw.removePrefix("<").removePrefix("[").removeSuffix(">").removeSuffix("]")
    val parts = inner.split(Regex("\\s+"))
    // 丢弃星期缩写（第二个 token）与 repeater/警告标记，保留日期与时间
    return parts.filterIndexed { index, part ->
        when {
            index == 0 -> true // 日期
            part.matches(Regex("\\d{1,2}:\\d{2}(-\\d{1,2}:\\d{2})?")) -> true // 时间或时间段
            else -> false
        }
    }.joinToString(" ")
}
