package com.dakala.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dakala.app.R
import com.dakala.app.domain.model.AppMonitorStatus
import com.dakala.app.domain.model.DayResetTime

/**
 * 时间选择对话框组件
 *
 * 用于选择通知时间（时 : 分滚轮）。
 *
 * @param initialHour 初始小时
 * @param initialMinute 初始分钟
 * @param onTimeSelected 时间选择回调
 * @param onDismiss 取消回调
 */
@Composable
fun TimePickerDialog(
    initialHour: Int = 22,
    initialMinute: Int = 0,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var hour by remember { mutableIntStateOf(initialHour.coerceIn(0, 23)) }
    var minute by remember { mutableIntStateOf(initialMinute.coerceIn(0, 59)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_time)) },
        text = {
            TimeWheelPicker(
                hour = hour,
                minute = minute,
                onHourChange = { hour = it },
                onMinuteChange = { minute = it },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onTimeSelected(hour, minute)
                    onDismiss()
                }
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
 * 时长阈值设置对话框组件
 *
 * 用于设置应用的时长阈值（时 : 分 : 秒滚轮 + 常用预设）。
 *
 * @param currentThreshold 当前阈值（秒）
 * @param title 对话框标题
 * @param description 对话框描述
 * @param onThresholdSelected 阈值选择回调
 * @param onDismiss 取消回调
 */
@Composable
fun DurationThresholdDialog(
    currentThreshold: Int = 600,
    title: String = "设置时长阈值",
    description: String = "设置应用需要使用的最小时长",
    onThresholdSelected: (seconds: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initial = currentThreshold.coerceIn(0, 23 * 3600 + 59 * 60 + 59)
    var hour by remember { mutableIntStateOf(initial / 3600) }
    var minute by remember { mutableIntStateOf(initial % 3600 / 60) }
    var second by remember { mutableIntStateOf(initial % 60) }

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

                DurationWheelPicker(
                    hour = hour,
                    minute = minute,
                    second = second,
                    onHourChange = { hour = it },
                    onMinuteChange = { minute = it },
                    onSecondChange = { second = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 常用预设
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5 * 60, 10 * 60, 30 * 60, 60 * 60).forEach { preset ->
                        SuggestionChip(
                            onClick = {
                                hour = preset / 3600
                                minute = preset % 3600 / 60
                                second = preset % 60
                            },
                            label = {
                                Text(AppMonitorStatus.formatDuration(preset))
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(
                        R.string.duration_wheel_total,
                        AppMonitorStatus.formatDuration(hour * 3600 + minute * 60 + second)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onThresholdSelected(hour * 3600 + minute * 60 + second)
                    onDismiss()
                }
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
 * 重置时间设置对话框组件
 *
 * 选择每日打卡周期的翻篇时刻（时 : 分滚轮 + 常用预设）。
 * 应用维度下提供「跟随全局」项，选中时不应用滚轮值。
 *
 * @param currentMinutes 当前重置点（距零点分钟数）；null 表示跟随全局
 * @param followGlobalLabel 「跟随全局」项的展示文本（含当前全局值）
 * @param showFollowOption 是否提供「跟随全局」选项（全局设置本身不显示）
 * @param onSelected 选中回调；null 表示跟随全局，否则为距零点分钟数
 * @param onDismiss 取消回调
 */
@Composable
fun ResetTimeDialog(
    currentMinutes: Int?,
    followGlobalLabel: String,
    showFollowOption: Boolean,
    onSelected: (minutesOfDay: Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val initial = (currentMinutes ?: 0).coerceIn(0, 23 * 60 + 59)
    var followGlobal by remember { mutableStateOf(showFollowOption && currentMinutes == null) }
    var hour by remember { mutableIntStateOf(initial / 60) }
    var minute by remember { mutableIntStateOf(initial % 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reset_time_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.reset_time_description),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                TimeWheelPicker(
                    hour = hour,
                    minute = minute,
                    onHourChange = { hour = it },
                    onMinuteChange = { minute = it },
                    enabled = !followGlobal,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showFollowOption) {
                        FilterChip(
                            selected = followGlobal,
                            onClick = { followGlobal = true },
                            label = { Text(followGlobalLabel) }
                        )
                    }
                    val presets = if (showFollowOption) {
                        listOf(23 * 60, 4 * 60)
                    } else {
                        listOf(0, 23 * 60, 4 * 60)
                    }
                    presets.forEach { preset ->
                        val selected = !followGlobal && hour * 60 + minute == preset
                        FilterChip(
                            selected = selected,
                            onClick = {
                                followGlobal = false
                                hour = preset / 60
                                minute = preset % 60
                            },
                            label = { Text(DayResetTime.ofMinutes(preset).format()) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelected(if (followGlobal) null else hour * 60 + minute)
                    onDismiss()
                }
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
 * 应用打卡设置对话框组件
 *
 * 单个监控应用的设置入口：目标时长与重置时间，
 * 点击行各自进入对应的滚轮编辑对话框。
 *
 * @param appName 应用显示名称
 * @param durationLabel 目标时长展示文本
 * @param resetLabel 重置时间展示文本
 * @param onEditDuration 点击目标时长行
 * @param onEditReset 点击重置时间行
 * @param onDismiss 关闭回调
 */
@Composable
fun AppCheckSettingsDialog(
    appName: String,
    durationLabel: String,
    resetLabel: String,
    onEditDuration: () -> Unit,
    onEditReset: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appName) },
        text = {
            Column {
                SettingsItem(
                    icon = Icons.Default.Timer,
                    title = stringResource(R.string.app_check_settings_target),
                    value = durationLabel,
                    onClick = onEditDuration
                )
                SettingsItem(
                    icon = Icons.Default.Schedule,
                    title = stringResource(R.string.app_check_settings_reset),
                    value = resetLabel,
                    onClick = onEditReset
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        }
    )
}
