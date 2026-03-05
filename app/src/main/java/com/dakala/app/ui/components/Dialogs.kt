package com.dakala.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.rememberTimePickerState

/**
 * 时间选择对话框组件
 *
 * 用于选择通知时间。
 *
 * @param initialHour 初始小时
 * @param initialMinute 初始分钟
 * @param onTimeSelected 时间选择回调
 * @param onDismiss 取消回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialHour: Int = 22,
    initialMinute: Int = 0,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .width(280.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "选择通知时间",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                TimePicker(
                    state = state,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            onTimeSelected(state.hour, state.minute)
                            onDismiss()
                        }
                    ) {
                        Text("确认")
                    }
                }
            }
        }
    }
}

/**
 * 时长阈值设置对话框组件
 *
 * 用于设置应用的时长阈值。
 *
 * @param currentThreshold 当前阈值（秒）
 * @param title 对话框标题
 * @param description 对话框描述
 * @param onThresholdSelected 阈值选择回调
 * @param onDismiss 取消回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationThresholdDialog(
    currentThreshold: Int = 600,
    title: String = "设置时长阈值",
    description: String = "设置应用需要使用的最小时长",
    onThresholdSelected: (seconds: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var minutes by remember { mutableStateOf(currentThreshold / 60) }
    var seconds by remember { mutableStateOf(currentThreshold % 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 分钟输入
                OutlinedTextField(
                    value = minutes.toString(),
                    onValueChange = {
                        minutes = it.toIntOrNull() ?: 0
                    },
                    label = { Text("分钟") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 秒数输入
                OutlinedTextField(
                    value = seconds.toString(),
                    onValueChange = {
                        seconds = (it.toIntOrNull() ?: 0).coerceIn(0, 59)
                    },
                    label = { Text("秒") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onThresholdSelected(minutes * 60 + seconds)
                    onDismiss()
                }
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
