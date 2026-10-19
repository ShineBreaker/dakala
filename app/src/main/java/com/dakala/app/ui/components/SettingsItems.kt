package com.dakala.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dakala.app.R
import kotlin.math.roundToInt

/**
 * 设置分区标题
 *
 * 设置页按用途分区（见 docs/DESIGN.md 第 4 节），各区之间用一行小标题分隔。
 */
@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * 设置项
 *
 * 每个设置项必须显示当前值或默认值：标题在上、当前值在下，整行可点。
 *
 * @param icon 行首图标
 * @param title 设置项名称
 * @param value 当前值或默认值的展示文本
 * @param onClick 点击行进入编辑
 */
@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/**
 * 开关型设置项
 *
 * 与 [SettingsItem] 同规则：标题在上、当前状态在下，尾部为 Switch；
 * 点行与拨开关等效。
 *
 * @param icon 行首图标
 * @param title 设置项名称
 * @param checked 当前开关状态
 * @param valueText 当前状态的展示文本（随 checked 变化）
 * @param onCheckedChange 开关回调
 */
@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    valueText: String,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(valueText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/**
 * 小部件透明度设置对话框
 *
 * 滑杆范围 10%–100%，低于 10% 内容会失去与桌面的边界。
 *
 * @param currentPercent 当前不透明度（百分比）
 * @param onConfirm 确认回调（百分比）
 * @param onDismiss 取消回调
 */
@Composable
fun WidgetOpacityDialog(
    currentPercent: Int,
    onConfirm: (percent: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var percent by remember { mutableFloatStateOf(currentPercent.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.widget_opacity_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.settings_widget_opacity_value, percent.roundToInt()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth()
                )
                Slider(
                    value = percent,
                    onValueChange = { percent = it.roundToInt().toFloat() },
                    valueRange = 10f..100f
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "10%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "100%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(percent.roundToInt())
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
