package com.dakala.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dakala.app.R
import com.dakala.app.data.local.entity.CustomCheckItem
import com.dakala.app.data.org.OrgNote
import com.dakala.app.domain.model.AppMonitorStatus
import com.dakala.app.ui.components.*
import com.dakala.app.ui.theme.DakalaTheme
import com.dakala.app.ui.util.PermissionHelper
import com.dakala.app.ui.viewmodel.MainViewModel
import com.dakala.app.ui.viewmodel.TodoUiState
import com.dakala.app.ui.viewmodel.TodoViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * 主Activity
 *
 * 应用的主界面，显示被监控应用的打卡状态和自定义打卡项。
 *
 * 主要功能：
 * 1. 显示已选应用列表，分为"未完成"和"已完成"两个区域
 * 2. 显示自定义打卡项列表
 * 3. 提供设置监控应用的入口
 * 4. 提供设置通知时间的入口
 * 5. 刷新应用使用统计
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
 * Tab 类型枚举
 */
enum class CheckTab(val title: String) {
    APP_CHECK("应用打卡"),
    CUSTOM_CHECK("自定义打卡"),
    ORG_TODO("待办")
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
    viewModel: MainViewModel = hiltViewModel(),
    todoViewModel: TodoViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val monitorStatusGroup by viewModel.monitorStatusGroup.collectAsState()
    val customCheckStatusGroup by viewModel.customCheckStatusGroup.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val todoUiState by todoViewModel.uiState.collectAsState()

    // 当前选中的Tab
    var selectedTab by remember { mutableStateOf(CheckTab.APP_CHECK) }

    // 权限检查状态
    var hasPermission by remember { mutableStateOf(false) }

    // 时长设置对话框状态
    var showDurationDialog by remember { mutableStateOf(false) }
    var selectedAppForDuration by remember { mutableStateOf<AppMonitorStatus?>(null) }

    // 自定义打卡对话框状态
    var showAddCustomCheckDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<CustomCheckItem?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deletingItem by remember { mutableStateOf<CustomCheckItem?>(null) }

    // 待办对话框状态
    var showAddTodoDialog by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<OrgNote?>(null) }
    var deletingTodo by remember { mutableStateOf<OrgNote?>(null) }

    // Org 文件选择器
    val openOrgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { todoViewModel.setOrgFile(it) }
    }

    // Org 文件新建器
    val createOrgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let { todoViewModel.setOrgFile(it) }
    }

    val pickOrgFile: () -> Unit = { openOrgLauncher.launch(arrayOf("*/*")) }
    val createOrgFile: () -> Unit = { createOrgLauncher.launch("todo.org") }

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
                Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
            )
        }
    }

    // 检测日期变更（跨天后自动刷新数据），同时刷新待办（捕获外部修改）
    LifecycleResumeEffect(Unit) {
        viewModel.checkDateAndRefresh()
        todoViewModel.refresh()
        onPauseOrDispose { }
    }

    // 切换到待办 Tab 时刷新文件内容
    LaunchedEffect(selectedTab) {
        if (selectedTab == CheckTab.ORG_TODO) {
            todoViewModel.refresh()
        }
    }

    Scaffold(
        topBar = {
            Column {
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
                        // 设置页入口
                        IconButton(
                            onClick = {
                                context.startActivity(Intent(context, SettingsActivity::class.java))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "设置"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    CheckTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.title) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    when (selectedTab) {
                        CheckTab.APP_CHECK -> onNavigateToAppSelection()
                        CheckTab.CUSTOM_CHECK -> showAddCustomCheckDialog = true
                        CheckTab.ORG_TODO -> {
                            if (todoUiState is TodoUiState.Ready) {
                                showAddTodoDialog = true
                            } else {
                                pickOrgFile()
                            }
                        }
                    }
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = {
                    Text(
                        when (selectedTab) {
                            CheckTab.APP_CHECK -> stringResource(R.string.settings_monitor_apps)
                            CheckTab.CUSTOM_CHECK -> stringResource(R.string.add_custom_check)
                            CheckTab.ORG_TODO -> {
                                if (todoUiState is TodoUiState.Ready) {
                                    stringResource(R.string.todo_add)
                                } else {
                                    stringResource(R.string.todo_pick_file)
                                }
                            }
                        }
                    )
                },
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
            when (selectedTab) {
                CheckTab.APP_CHECK -> {
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
                            if (monitorStatusGroup.hasIncomplete) {
                                item {
                                    SectionHeader(
                                        title = stringResource(R.string.incomplete_section),
                                        count = monitorStatusGroup.incomplete.size
                                    )
                                }
                                items(monitorStatusGroup.incomplete) { status ->
                                    AppMonitorCard(
                                        status = status,
                                        onAppClick = { packageName ->
                                            openApp(context, packageName)
                                        },
                                        onSettingsClick = { packageName ->
                                            selectedAppForDuration = monitorStatusGroup.incomplete.find { it.appItem.packageName == packageName }
                                            showDurationDialog = true
                                        }
                                    )
                                }
                            }

                            // 已完成区域
                            if (monitorStatusGroup.hasCompleted) {
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    SectionHeader(
                                        title = stringResource(R.string.completed_section),
                                        count = monitorStatusGroup.completed.size
                                    )
                                }
                                items(monitorStatusGroup.completed) { status ->
                                    AppMonitorCard(
                                        status = status,
                                        onAppClick = { packageName ->
                                            openApp(context, packageName)
                                        },
                                        onSettingsClick = { packageName ->
                                            selectedAppForDuration = monitorStatusGroup.completed.find { it.appItem.packageName == packageName }
                                            showDurationDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                CheckTab.CUSTOM_CHECK -> {
                    CustomCheckList(
                        statusGroup = customCheckStatusGroup,
                        onCheckToggle = { itemId, isCompleted ->
                            viewModel.toggleCustomCheckStatus(itemId, isCompleted)
                        },
                        onEdit = { item ->
                            editingItem = item
                        },
                        onDelete = { itemId ->
                            deletingItem = customCheckStatusGroup.incomplete
                                .find { it.item.id == itemId }?.item
                                ?: customCheckStatusGroup.completed
                                    .find { it.item.id == itemId }?.item
                            showDeleteConfirmDialog = true
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                CheckTab.ORG_TODO -> {
                    when (val state = todoUiState) {
                        TodoUiState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        TodoUiState.NoFile -> {
                            TodoNoFileState(
                                onPickFile = pickOrgFile,
                                onCreateFile = createOrgFile,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        is TodoUiState.Error -> {
                            TodoErrorState(
                                message = state.message,
                                showPickFile = state.permissionError,
                                onPickFile = pickOrgFile,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        is TodoUiState.Ready -> {
                            if (state.notes.isEmpty()) {
                                EmptyState(
                                    message = stringResource(R.string.todo_empty),
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else {
                                TodoList(
                                    notes = state.notes,
                                    onToggle = { note -> todoViewModel.toggleNote(note) },
                                    onEdit = { note -> editingTodo = note },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 时长设置对话框
    if (showDurationDialog && selectedAppForDuration != null) {
        DurationThresholdDialog(
            currentThreshold = selectedAppForDuration!!.appItem.durationThreshold,
            onThresholdSelected = { seconds ->
                viewModel.updateDurationThreshold(
                    selectedAppForDuration!!.appItem.packageName,
                    seconds
                )
                showDurationDialog = false
                selectedAppForDuration = null
            },
            onDismiss = {
                showDurationDialog = false
                selectedAppForDuration = null
            }
        )
    }

    // 添加自定义打卡对话框
    if (showAddCustomCheckDialog) {
        AddCustomCheckDialog(
            onConfirm = { name, iconType, iconData ->
                viewModel.addCustomCheckItem(name, iconType, iconData)
                showAddCustomCheckDialog = false
            },
            onDismiss = { showAddCustomCheckDialog = false }
        )
    }

    // 编辑自定义打卡对话框
    if (editingItem != null) {
        AddCustomCheckDialog(
            item = editingItem,
            onConfirm = { name, iconType, iconData ->
                viewModel.updateCustomCheckItem(
                    editingItem!!.copy(name = name, iconType = iconType, iconData = iconData)
                )
                editingItem = null
            },
            onDismiss = { editingItem = null }
        )
    }

    // 添加待办对话框
    if (showAddTodoDialog) {
        AddTodoDialog(
            onConfirm = { title ->
                todoViewModel.addTodo(title)
                showAddTodoDialog = false
            },
            onDismiss = { showAddTodoDialog = false }
        )
    }

    // 编辑待办对话框（点卡片进入；删除在其中二次确认）
    editingTodo?.let { note ->
        EditTodoDialog(
            note = note,
            allStates = (todoUiState as? TodoUiState.Ready)?.states?.allKeywords
                ?: listOf("TODO", "DONE"),
            onConfirm = { title, state, priority, tags, scheduled, deadline ->
                todoViewModel.updateTodo(note, title, state, priority, tags, scheduled, deadline)
                editingTodo = null
            },
            onDelete = {
                deletingTodo = note
                editingTodo = null
            },
            onDismiss = { editingTodo = null }
        )
    }

    // 删除待办确认对话框
    deletingTodo?.let { note ->
        DeleteTodoDialog(
            note = note,
            onConfirm = {
                todoViewModel.deleteTodo(note)
                deletingTodo = null
            },
            onDismiss = { deletingTodo = null }
        )
    }

    // 删除确认对话框
    if (showDeleteConfirmDialog && deletingItem != null) {
        DeleteConfirmDialog(
            itemName = deletingItem!!.name,
            onConfirm = {
                viewModel.deleteCustomCheckItem(deletingItem!!.id)
                showDeleteConfirmDialog = false
                deletingItem = null
            },
            onDismiss = {
                showDeleteConfirmDialog = false
                deletingItem = null
            }
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
    } catch (_: Exception) {
        // 应用无法打开
    }
}