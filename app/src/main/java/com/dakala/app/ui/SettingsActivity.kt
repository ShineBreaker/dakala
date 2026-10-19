package com.dakala.app.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.dakala.app.R
import com.dakala.app.domain.model.AppMonitorStatus
import com.dakala.app.domain.model.NotificationTime
import com.dakala.app.domain.model.WidgetOpacity
import com.dakala.app.ui.components.DurationThresholdDialog
import com.dakala.app.ui.components.SettingsItem
import com.dakala.app.ui.components.SettingsSectionHeader
import com.dakala.app.ui.components.SettingsSwitchItem
import com.dakala.app.ui.components.TimePickerDialog
import com.dakala.app.ui.components.WidgetOpacityDialog
import com.dakala.app.ui.theme.DakalaTheme
import com.dakala.app.ui.util.PermissionHelper
import com.dakala.app.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * 设置Activity
 *
 * 统一收纳全部用户设置：每个设置项显示当前值或默认值，
 * 点击行进入编辑（见 docs/DESIGN.md 第 4 节）。
 */
@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DakalaTheme {
                SettingsScreen(
                    onBack = { finish() },
                    onNavigateToAppSelection = {
                        startActivity(Intent(this, AppSelectionActivity::class.java))
                    }
                )
            }
        }
    }
}

/**
 * 设置界面Composable
 *
 * @param onBack 返回回调
 * @param onNavigateToAppSelection 导航到应用选择界面的回调
 * @param viewModel SettingsViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAppSelection: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val notificationTime by viewModel.notificationTime.collectAsState()
    val defaultDurationThreshold by viewModel.defaultDurationThreshold.collectAsState()
    val monitoredAppCount by viewModel.monitoredAppCount.collectAsState()
    val orgFilePath by viewModel.orgFilePath.collectAsState()
    val widgetOpacity by viewModel.widgetOpacity.collectAsState()
    val widgetTodoShowAll by viewModel.widgetTodoShowAll.collectAsState()

    var hasUsagePermission by remember { mutableStateOf(false) }

    var showTimePicker by remember { mutableStateOf(false) }
    var showDurationDialog by remember { mutableStateOf(false) }
    var showOpacityDialog by remember { mutableStateOf(false) }

    // Org 文件选择器
    val openOrgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.setOrgFile(it) }
    }

    // 返回本页时刷新权限状态与设置值（可能在系统页里刚改过）
    LifecycleResumeEffect(Unit) {
        hasUsagePermission = PermissionHelper.checkUsageStatsPermission(context)
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // 打卡
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_check))
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Apps,
                    title = stringResource(R.string.settings_monitored_apps),
                    value = stringResource(R.string.settings_monitored_apps_value, monitoredAppCount),
                    onClick = onNavigateToAppSelection
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.settings_notification_time),
                    value = notificationTime.format(),
                    onClick = { showTimePicker = true }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Timer,
                    title = stringResource(R.string.settings_default_duration),
                    value = AppMonitorStatus.formatDuration(defaultDurationThreshold),
                    onClick = { showDurationDialog = true }
                )
            }

            // 待办
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_todo))
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Description,
                    title = stringResource(R.string.settings_org_file),
                    value = orgFilePath ?: stringResource(R.string.settings_org_file_none),
                    onClick = { openOrgLauncher.launch(arrayOf("*/*")) }
                )
            }

            // 小部件
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_widget))
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Widgets,
                    title = stringResource(R.string.settings_widget_opacity),
                    value = stringResource(R.string.settings_widget_opacity_value, widgetOpacity.percent),
                    onClick = { showOpacityDialog = true }
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Checklist,
                    title = stringResource(R.string.settings_widget_todos),
                    checked = widgetTodoShowAll,
                    valueText = stringResource(
                        if (widgetTodoShowAll) {
                            R.string.settings_widget_todos_all
                        } else {
                            R.string.settings_widget_todos_incomplete
                        }
                    ),
                    onCheckedChange = { viewModel.setWidgetTodoShowAll(it) }
                )
            }

            // 权限
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_permission))
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.settings_usage_permission),
                    value = stringResource(
                        if (hasUsagePermission) {
                            R.string.settings_permission_granted
                        } else {
                            R.string.settings_permission_denied
                        }
                    ),
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )
            }
        }
    }

    // 时间选择对话框
    if (showTimePicker) {
        TimePickerDialog(
            initialHour = notificationTime.hour,
            initialMinute = notificationTime.minute,
            onTimeSelected = { h, m ->
                viewModel.setNotificationTime(NotificationTime(h, m))
            },
            onDismiss = { showTimePicker = false }
        )
    }

    // 默认时长设置对话框
    if (showDurationDialog) {
        DurationThresholdDialog(
            currentThreshold = defaultDurationThreshold,
            title = "设置默认时长",
            description = "新添加应用的默认目标时长",
            onThresholdSelected = { seconds ->
                viewModel.setDefaultDurationThreshold(seconds)
                showDurationDialog = false
            },
            onDismiss = { showDurationDialog = false }
        )
    }

    // 小部件透明度设置对话框
    if (showOpacityDialog) {
        WidgetOpacityDialog(
            currentPercent = widgetOpacity.percent,
            onConfirm = { percent ->
                viewModel.setWidgetOpacity(WidgetOpacity(percent))
            },
            onDismiss = { showOpacityDialog = false }
        )
    }
}
