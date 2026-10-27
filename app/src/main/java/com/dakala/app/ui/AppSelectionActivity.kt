package com.dakala.app.ui

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dakala.app.data.local.entity.AppItem
import com.dakala.app.ui.components.drawableToBitmap
import com.dakala.app.ui.theme.DakalaTheme
import com.dakala.app.ui.viewmodel.AppSelectionViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用选择Activity
 *
 * 用于选择要监控的应用。
 *
 * 主要功能：
 * 1. 显示系统已安装的应用列表
 * 2. 支持搜索过滤
 * 3. 支持多选
 * 4. 保存选择结果到数据库
 */
@AndroidEntryPoint
class AppSelectionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DakalaTheme {
                AppSelectionScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

/**
 * 应用选择界面Composable
 *
 * @param onBack 返回回调
 * @param viewModel AppSelectionViewModel
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppSelectionScreen(
    onBack: () -> Unit,
    viewModel: AppSelectionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val filteredApps by viewModel.filteredApps.collectAsState()
    val selectedPackages by viewModel.selectedPackages.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // 搜索状态：TextFieldState 持有查询文本，实时同步给 ViewModel 过滤
    var isSearching by remember { mutableStateOf(false) }
    val searchBarState = rememberSearchBarState()
    val searchTextFieldState = rememberTextFieldState(searchQuery)

    LaunchedEffect(searchTextFieldState) {
        snapshotFlow { searchTextFieldState.text.toString() }
            .collect { viewModel.setSearchQuery(it) }
    }

    Scaffold(
        topBar = {
            if (isSearching) {
                // 搜索模式：M3 顶置搜索栏
                AppBarWithSearch(
                    state = searchBarState,
                    inputField = {
                        SearchBarDefaults.InputField(
                            textFieldState = searchTextFieldState,
                            searchBarState = searchBarState,
                            onSearch = {},
                            placeholder = { Text("搜索应用") },
                            leadingIcon = {
                                IconButton(onClick = {
                                    isSearching = false
                                    searchTextFieldState.clearText()
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                // 正常模式
                TopAppBar(
                    title = { Text("选择监控应用") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, "搜索")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            // 底部操作栏（M3 Expressive 浮动工具栏 + 保存 FAB）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                HorizontalFloatingToolbar(
                    expanded = true,
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = {
                                viewModel.saveSelectedApps()
                                onBack()
                            },
                            icon = {
                                Icon(Icons.Default.Check, contentDescription = null)
                            },
                            text = { Text("保存") }
                        )
                    }
                ) {
                    Text(
                        text = "已选择 ${selectedPackages.size} 个应用",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    TextButton(onClick = { viewModel.deselectAll() }) {
                        Text("取消全选")
                    }
                }
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppSelectionItem(
                        app = app,
                        isSelected = app.packageName in selectedPackages,
                        onClick = { viewModel.toggleAppSelection(app.packageName) }
                    )
                }
            }
        }
    }
}

/**
 * 应用选择项组件
 *
 * @param app 应用信息
 * @param isSelected 是否已选中
 * @param onClick 点击回调
 */
@Composable
fun AppSelectionItem(
    app: AppItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    // 应用图标：包管理器读取与位图转换放 IO 线程，避免滚动期间阻塞组合
    val appIcon by produceState<Bitmap?>(initialValue = null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                drawableToBitmap(context.packageManager.getApplicationIcon(app.packageName))
            } catch (e: Exception) {
                null
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 应用图标：固定占位，解码完成前不挤压布局
        Box(modifier = Modifier.size(40.dp)) {
            appIcon?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = app.appName,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 应用名称
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 选中状态（行整行可点，Checkbox 只做状态展示）
        Checkbox(
            checked = isSelected,
            onCheckedChange = null
        )
    }
}
