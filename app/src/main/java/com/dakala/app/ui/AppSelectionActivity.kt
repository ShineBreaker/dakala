package com.dakala.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dakala.app.data.local.entity.AppItem
import com.dakala.app.ui.theme.DakalaTheme
import com.dakala.app.ui.viewmodel.AppSelectionViewModel
import dagger.hilt.android.AndroidEntryPoint

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
@OptIn(ExperimentalMaterial3Api::class)
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
    
    // 搜索状态
    var isSearching by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf(searchQuery) }

    Scaffold(
        topBar = {
            if (isSearching) {
                // 搜索模式
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { 
                                searchText = it
                                viewModel.setSearchQuery(it)
                            },
                            placeholder = { Text("搜索应用") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            isSearching = false
                            searchText = ""
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
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
            // 底部操作栏
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已选择 ${selectedPackages.size} 个应用",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row {
                        TextButton(onClick = { viewModel.deselectAll() }) {
                            Text("取消全选")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            viewModel.saveSelectedApps()
                            onBack()
                        }) {
                            Text("保存")
                        }
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
                CircularProgressIndicator()
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
    
    // 获取应用图标
    val appIcon = remember(app.packageName) {
        try {
            context.packageManager.getApplicationIcon(app.packageName)
        } catch (e: Exception) {
            context.getDrawable(android.R.drawable.sym_def_app_icon)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 应用图标
        appIcon?.let { drawable ->
            androidx.compose.foundation.Image(
                bitmap = android.graphics.drawable.BitmapDrawable(
                    context.resources,
                    com.dakala.app.ui.components.drawableToBitmap(drawable)
                ).bitmap.asImageBitmap(),
                contentDescription = app.appName,
                modifier = Modifier.size(40.dp)
            )
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
        
        // 选中状态
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已选中",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// 需要导入的asImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.dakala.app.ui.components.drawableToBitmap