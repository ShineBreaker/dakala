package com.dakala.app.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.dakala.app.R
import com.dakala.app.data.org.OrgNote
import com.dakala.app.data.org.OrgWriter
import com.dakala.app.data.org.parseOrgTimestamp
import java.time.LocalDate

/**
 * Org 待办列表组件
 *
 * 按完成状态分两组展示：未完成在前（保持文件内顺序），已完成在后。
 */
@Composable
fun TodoList(
    notes: List<OrgNote>,
    onToggle: (OrgNote) -> Unit,
    onEdit: (OrgNote) -> Unit,
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
                TodoCard(note = note, onToggle = { onToggle(note) }, onClick = { onEdit(note) })
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
                TodoCard(note = note, onToggle = { onToggle(note) }, onClick = { onEdit(note) })
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
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
 * 编辑待办对话框
 *
 * 可编辑字段：标题、状态、优先级（无/A/B/C）、tags、SCHEDULED、DEADLINE；
 * 保存时整组字段一次性写回（org 文件是唯一数据源，对话框值即最终值）。
 * 删除入口内嵌于本对话框（破坏性操作需二次确认）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditTodoDialog(
    note: OrgNote,
    allStates: List<String>,
    onConfirm: (
        title: String,
        state: String,
        priority: String,
        tags: List<String>,
        scheduled: String,
        deadline: String
    ) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(note.title) }
    var state by remember { mutableStateOf(note.state) }
    var priority by remember { mutableStateOf(note.priority ?: "") }
    var tagsText by remember { mutableStateOf(note.tags.joinToString(" ")) }
    var scheduled by remember { mutableStateOf(note.scheduled ?: "") }
    var deadline by remember { mutableStateOf(note.deadline ?: "") }

    /** 弹出日期选择器；选中后保留原时间分量（若有），产出 org 主动时间戳 */
    fun pickDate(current: String, onPicked: (String) -> Unit) {
        val parsed = if (current.isNotEmpty()) parseOrgTimestamp(current) else null
        val init = parsed?.first ?: LocalDate.now()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                onPicked(
                    OrgWriter.formatTimestamp(
                        LocalDate.of(year, month + 1, day),
                        parsed?.second
                    )
                )
            },
            init.year,
            init.monthValue - 1,
            init.dayOfMonth
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.todo_edit)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.todo_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                DialogFieldLabel(stringResource(R.string.todo_state_label))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allStates.forEach { keyword ->
                        FilterChip(
                            selected = keyword == state,
                            onClick = { state = keyword },
                            label = { Text(keyword) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                DialogFieldLabel(stringResource(R.string.todo_priority_label))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("", "A", "B", "C").forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = {
                                Text(
                                    if (p.isEmpty()) {
                                        stringResource(R.string.todo_priority_none)
                                    } else {
                                        p
                                    }
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text(stringResource(R.string.todo_tags_label)) },
                    placeholder = { Text(stringResource(R.string.todo_tags_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                PlanningRow(
                    label = stringResource(R.string.todo_scheduled),
                    value = scheduled,
                    onPick = { pickDate(scheduled) { scheduled = it } },
                    onClear = { scheduled = "" }
                )
                PlanningRow(
                    label = stringResource(R.string.todo_deadline),
                    value = deadline,
                    onPick = { pickDate(deadline) { deadline = it } },
                    onClear = { deadline = "" }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val tags = tagsText.split(Regex("[\\s,，:]+"))
                        .filter { it.isNotEmpty() }
                    onConfirm(title.trim(), state, priority, tags, scheduled, deadline)
                },
                enabled = title.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

/** 对话框内的小字段标签 */
@Composable
private fun DialogFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
}

/** SCHEDULED / DEADLINE 行：标签 + 当前日期（可点开重选）+ 清除 */
@Composable
private fun PlanningRow(
    label: String,
    value: String,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onPick) {
            Text(
                if (value.isEmpty()) {
                    stringResource(R.string.not_set)
                } else {
                    formatOrgTimestamp(value)
                }
            )
        }
        if (value.isNotEmpty()) {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.clear))
            }
        }
    }
}

/**
 * 删除待办二次确认对话框
 */
@Composable
fun DeleteTodoDialog(
    note: OrgNote,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.todo_delete)) },
        text = {
            Text(
                stringResource(
                    R.string.todo_delete_confirm,
                    note.title.ifEmpty { stringResource(R.string.todo_untitled) }
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error
                )
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
