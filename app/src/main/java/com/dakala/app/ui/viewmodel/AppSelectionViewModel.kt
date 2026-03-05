package com.dakala.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dakala.app.data.local.entity.AppItem
import com.dakala.app.data.repository.AppUsageRepository
import com.dakala.app.domain.usecase.UsageStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 应用选择界面ViewModel
 *
 * 负责管理应用选择界面的数据和业务逻辑，包括：
 * 1. 加载系统已安装的应用列表
 * 2. 管理应用的选中状态
 * 3. 保存用户选择的应用到数据库
 *
 * @property application 应用上下文
 * @property repository 数据仓库
 */
@HiltViewModel
class AppSelectionViewModel @Inject constructor(
    application: Application,
    private val repository: AppUsageRepository,
    private val usageStatsUseCase: UsageStatsUseCase
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AppSelectionViewModel"
    }

    /**
     * 系统已安装应用列表
     */
    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())
    val installedApps: StateFlow<List<AppItem>> = _installedApps.asStateFlow()

    /**
     * 已选中的应用包名集合
     */
    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackages: StateFlow<Set<String>> = _selectedPackages.asStateFlow()

    /**
     * 加载状态
     */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.value.let { MutableStateFlow(it) }

    /**
     * 搜索关键词
     */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * 过滤后的应用列表
     */
    val filteredApps: StateFlow<List<AppItem>> = combine(
        _installedApps,
        _selectedPackages,
        _searchQuery
    ) { apps, selected, query ->
        apps.filter { app ->
            if (query.isBlank()) true
            else app.appName.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
        }.sortedWith(compareBy<AppItem> { it.packageName !in selected }.thenBy { it.appName.lowercase() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ==================== 初始化 ====================

    init {
        loadInstalledApps()
    }

    // ==================== 公共方法 ====================

    /**
     * 加载系统已安装的应用列表
     */
    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val packageManager = getApplication<Application>().packageManager

                // 获取所有可启动的应用
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfoList = packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_ALL)

                // 获取已监控的应用
                val monitoredApps = repository.getMonitoredApps()
                val monitoredPackageNames = monitoredApps.map { it.packageName }.toSet()

                // 构建应用列表
                val appList = resolveInfoList
                    .map { resolveInfo ->
                        val appInfo = resolveInfo.activityInfo.applicationInfo
                        AppItem(
                            packageName = appInfo.packageName,
                            appName = appInfo.loadLabel(packageManager).toString(),
                            isMonitored = appInfo.packageName in monitoredPackageNames
                        )
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.appName.lowercase() }

                _installedApps.value = appList
                _selectedPackages.value = monitoredPackageNames

                Log.d(TAG, "加载已安装应用: ${appList.size}个, 已选中: ${monitoredPackageNames.size}个")
            } catch (e: Exception) {
                Log.e(TAG, "加载已安装应用失败", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 切换应用的选中状态
     *
     * @param packageName 应用包名
     */
    fun toggleAppSelection(packageName: String) {
        val currentSelected = _selectedPackages.value.toMutableSet()
        if (packageName in currentSelected) {
            currentSelected.remove(packageName)
        } else {
            currentSelected.add(packageName)
        }
        _selectedPackages.value = currentSelected
    }

    /**
     * 全选所有应用
     */
    fun selectAll() {
        _selectedPackages.value = _installedApps.value.map { it.packageName }.toSet()
    }

    /**
     * 取消全选
     */
    fun deselectAll() {
        _selectedPackages.value = emptySet()
    }

    /**
     * 设置搜索关键词
     *
     * @param query 搜索关键词
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * 保存选择的应用到数据库
     */
    fun saveSelectedApps() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val selectedPackageNames = _selectedPackages.value
                val allApps = _installedApps.value

                // 获取默认时长阈值
                val defaultThreshold = repository.getDefaultDurationThreshold()

                // 构建要保存的应用列表
                val appsToSave = allApps
                    .filter { it.packageName in selectedPackageNames }
                    .map { app ->
                        app.copy(
                            isMonitored = true,
                            durationThreshold = defaultThreshold
                        )
                    }

                // 获取当前已监控的应用
                val currentMonitoredApps = repository.getMonitoredApps()
                val currentPackageNames = currentMonitoredApps.map { it.packageName }.toSet()

                // 删除不再监控的应用
                for (app in currentMonitoredApps) {
                    if (app.packageName !in selectedPackageNames) {
                        repository.removeAppFromMonitor(app.packageName)
                    }
                }

                // 添加新监控的应用
                for (app in appsToSave) {
                    if (app.packageName !in currentPackageNames) {
                        repository.addAppToMonitor(app)
                    }
                }

                Log.d(TAG, "保存选择的应用: ${appsToSave.size}个")
            } catch (e: Exception) {
                Log.e(TAG, "保存选择的应用失败", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
