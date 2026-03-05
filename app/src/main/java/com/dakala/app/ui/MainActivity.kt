package com.dakala.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dakala.app.R
import com.dakala.app.ui.components.*
import com.dakala.app.ui.theme.DakalaTheme
import com.dakala.app.ui.util.PermissionHelper
import com.dakala.app.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * 主Activity
 * 
 * 应用的主界面，显示被监控应用的打卡状态。
 * 
 * 主要功能：
 * 1. 显示已选应用列表，分为"未完成"和"已完成"两个区域
 * 2. 提供设置监控应用的入口
 * 3. 提供设置通知时间的入口
 * 4. 刷新应用使用统计
 * 
 * 权限处理：
 * - 启动时检查PACKAGE_USAGE_STATS权限
 * - 未授权时引导用户到系统设置开启
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            DakalaTheme {
                MainScreen(
                    onNavigateToAppSelection = {
                        startActivity(Intent(this, AppSelectionActivity::class.java))
                    }
                )
            }
        }
    }
}

/**
 * 主界面Composable
 * 
 * @param onNavigateToAppSelection 导航到应用选择界面的回调
 * @param viewModel MainViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToAppSelection: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val monitorStatusGroup by viewModel.monitorStatusGroup.collectAsState()
    val notificationTime by viewModel.notificationTime.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // 权限检查状态
    var hasPermission by remember { mutableStateOf(false) }
    
    // 时间选择对话框状态
    var showTimePicker by remember { mutableStateOf(false) }
    
    // 权限请求启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // 从设置返回后重新检查权限
        hasPermission = PermissionHelper.checkUsageStatsPermission(context)
        if (hasPermission) {
            viewModel.refreshUsageStats()
        }
    }

    // 初始化时检查权限
    LaunchedEffect(Unit) {
        hasPermission = PermissionHelper.checkUsageStatsPermission(context)
        if (!hasPermission) {
            PermissionHelper.showPermissionGuide(context)
            permissionLauncher.launch(
                android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
            )
        }
    }

    // 解析通知时间
    val (hour, minute) = remember(notificationTime) {
        val parts = notificationTime.split(":")
        (parts.getOrNull(0)?.toIntOrNull() ?: 22) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.app_name))
                },
                actions = {
                    // 刷新按钮
                    IconButton(onClick = { viewModel.refreshUsageStats() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                    // 设置通知时间按钮
                    IconButton(onClick = { showTimePicker = true }) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = stringResource(R.string.settings_notification_time)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAppSelection,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.settings_monitor_apps)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (monitorStatusGroup.isEmpty) {
                EmptyState(
                    message = stringResource(R.string.no_monitored_apps),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 未完成区域
                    if (monitorStatusGroup.hasIncompleteApps) {
                        item {
                            SectionHeader(
                                title = stringResource(R.string.incomplete_section),
                                count = monitorStatusGroup.incompleteApps.size
                            )
                        }
                        items(monitorStatusGroup.incompleteApps) { status ->
                            AppMonitorCard(
                                status = status,
                                onAppClick = { packageName ->
                                    openApp(context, packageName)
                                }
                            )
                        }
                    }
                    
                    // 已完成区域
                    if (monitorStatusGroup.hasCompletedApps) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            SectionHeader(
                                title = stringResource(R.string.completed_section),
                                count = monitorStatusGroup.completedApps.size
                            )
                        }
                        items(monitorStatusGroup.completedApps) { status ->
                            AppMonitorCard(
                                status = status,
                                onAppClick = { packageName ->
                                    openApp(context, packageName)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 时间选择对话框
    if (showTimePicker) {
        TimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onTimeSelected = { h, m ->
                val timeString = String.format("%02d:%02d", h, m)
                viewModel.setNotificationTime(timeString)
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

/**
 * 打开指定应用
 * 
 * @param context 上下文
 * @param packageName 应用包名
 */
private fun openApp(context: android.content.Context, packageName: String) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        // 应用无法打开
    }
}

// 需要导入的rememberLauncherForActivityResult
import androidx.activity.result.rememberLauncherForActivityResult