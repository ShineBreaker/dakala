package com.dakala.app.ui.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dakala.app.data.local.entity.AppItem
import com.dakala.app.data.local.entity.CustomCheckItem
import com.dakala.app.data.repository.AppUsageRepository
import com.dakala.app.domain.model.AppMonitorStatus
import com.dakala.app.domain.model.CustomCheckStatus
import com.dakala.app.domain.model.CustomCheckStatusGroup
import com.dakala.app.domain.model.MonitorStatusGroup
import com.dakala.app.domain.usecase.UsageStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * 主界面ViewModel
 *
 * 负责管理主界面的数据和业务逻辑，包括：
 * 1. 加载和显示被监控的应用列表
 * 2. 获取和更新应用使用时长
 * 3. 管理通知时间设置
 * 4. 提供应用选择功能
 *
 * @property application 应用上下文
 * @property repository 数据仓库
 * @property usageStatsUseCase 使用统计用例
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repository: AppUsageRepository,
    private val usageStatsUseCase: UsageStatsUseCase
) : AndroidViewModel(application) {

    // ==================== UI状态 ====================

    /**
     * 当前追踪的日期（yyyyMMdd），用于检测跨天
     */
    private var lastRefreshDate: Int = getCurrentDate()

    /**
     * 今日使用时长映射（包名 -> 秒数）
     */
    private val _todayUsageMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    @Suppress("unused")
    val todayUsageMap: StateFlow<Map<String, Int>> = _todayUsageMap.asStateFlow()

    /**
     * 当前日期（响应式），用于驱动自定义打卡记录的日期查询
     */
    private val _currentDate = MutableStateFlow(getCurrentDate())

    /**
     * 监控状态分组（按完成状态分组）
     */
    val monitorStatusGroup: StateFlow<MonitorStatusGroup> = combine(
        repository.getMonitoredAppsFlow(),
        _todayUsageMap
    ) { apps, usageMap ->
        val statusList = apps.map { appItem ->
            AppMonitorStatus(
                appItem = appItem,
                todayDurationSeconds = usageMap[appItem.packageName] ?: 0
            )
        }

        MonitorStatusGroup(
            incompleteApps = statusList.filter { !it.isCompleted },
            completedApps = statusList.filter { it.isCompleted }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MonitorStatusGroup()
    )

    /**
     * 通知时间
     */
    private val _notificationTime = MutableStateFlow("22:00")
    val notificationTime: StateFlow<String> = _notificationTime.asStateFlow()

    /**
     * 默认时长阈值（秒）
     */
    private val _defaultDurationThreshold = MutableStateFlow(600)
    val defaultDurationThreshold: StateFlow<Int> = _defaultDurationThreshold.asStateFlow()

    /**
     * 加载状态
     */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * 错误信息
     */
    private val _errorMessage = MutableStateFlow<String?>(null)
    @Suppress("unused")
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * 系统已安装应用列表（用于应用选择界面）
     */
    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())
    @Suppress("unused")
    val installedApps: StateFlow<List<AppItem>> = _installedApps.asStateFlow()

    // ==================== 初始化 ====================

    init {
        loadNotificationTime()
        loadDefaultDurationThreshold()
        refreshUsageStats()
    }

    /**
     * 检测日期变更并刷新数据。
     * 应在 Activity.onResume 时调用。
     */
    fun checkDateAndRefresh() {
        val today = getCurrentDate()
        if (today != lastRefreshDate) {
            Log.d(TAG, "检测到日期变更: $lastRefreshDate -> $today")
            lastRefreshDate = today
            _currentDate.value = today
            refreshUsageStats(forceReset = true)
        }
    }

    // ==================== 公共方法 ====================

    /**
     * 刷新应用使用统计
     *
     * 从UsageStatsManager获取最新的使用数据，并更新到数据库和UI。
     *
     * @param forceReset 是否强制重置（跨天时使用，先清空旧数据再查询）
     */
    fun refreshUsageStats(forceReset: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 获取所有被监控的应用
                val monitoredApps = repository.getMonitoredApps()

                if (monitoredApps.isEmpty()) {
                    _todayUsageMap.value = emptyMap()
                    _isLoading.value = false
                    return@launch
                }

                // 强制重置时先清空数据，确保 StateFlow 必然触发 UI 更新
                if (forceReset) {
                    _todayUsageMap.value = emptyMap()
                }

                // 批量获取使用时长
                val packageNames = monitoredApps.map { it.packageName }
                val usageMap = usageStatsUseCase.getAppsUsageDuration(packageNames)

                // 更新数据库记录
                val today = getTodayDate()
                val records = monitoredApps.map { app ->
                    com.dakala.app.data.local.entity.UsageRecord(
                        packageName = app.packageName,
                        date = today,
                        durationSeconds = usageMap[app.packageName] ?: 0
                    )
                }
                repository.updateUsageRecords(records)

                // 更新UI状态：创建新Map实例，即使内容相同也触发StateFlow
                _todayUsageMap.value = usageMap.toMap()

                Log.d(TAG, "刷新使用统计完成: ${usageMap.size}个应用")
            } catch (e: Exception) {
                Log.e(TAG, "刷新使用统计失败", e)
                _errorMessage.value = "刷新使用统计失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 加载通知时间设置
     */
    private fun loadNotificationTime() {
        viewModelScope.launch {
            _notificationTime.value = repository.getNotificationTime()
        }
    }

    /**
     * 设置通知时间
     *
     * @param time 通知时间（格式：HH:mm）
     */
    fun setNotificationTime(time: String) {
        viewModelScope.launch {
            repository.setNotificationTime(time)
            _notificationTime.value = time
            Log.d(TAG, "通知时间已设置为: $time")
        }
    }

    /**
     * 加载默认时长阈值设置
     */
    private fun loadDefaultDurationThreshold() {
        viewModelScope.launch {
            _defaultDurationThreshold.value = repository.getDefaultDurationThreshold()
        }
    }

    /**
     * 设置默认时长阈值
     *
     * @param threshold 时长阈值（秒）
     */
    fun setDefaultDurationThreshold(threshold: Int) {
        viewModelScope.launch {
            repository.setDefaultDurationThreshold(threshold)
            _defaultDurationThreshold.value = threshold
            Log.d(TAG, "默认时长阈值已设置为: $threshold 秒")
        }
    }

    /**
     * 加载系统已安装的应用列表
     *
     * 用于应用选择界面，获取所有可启动的应用。
     */
    @Suppress("unused")
    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val packageManager = getApplication<Application>().packageManager
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                }

                val resolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)

                // 获取已监控的应用包名
                val monitoredApps = repository.getMonitoredApps()
                val monitoredPackageNames = monitoredApps.map { it.packageName }.toSet()

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
                Log.d(TAG, "加载已安装应用: ${appList.size}个")
            } catch (e: Exception) {
                Log.e(TAG, "加载已安装应用失败", e)
                _errorMessage.value = "加载应用列表失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 添加应用到监控列表
     *
     * @param packageName 应用包名
     * @param appName 应用名称
     */
    @Suppress("unused")
    fun addAppToMonitor(packageName: String, appName: String) {
        viewModelScope.launch {
            val defaultThreshold = repository.getDefaultDurationThreshold()
            val app = AppItem(
                packageName = packageName,
                appName = appName,
                isMonitored = true,
                durationThreshold = defaultThreshold
            )
            repository.addAppToMonitor(app)
            Log.d(TAG, "添加应用到监控: $packageName")
        }
    }

    /**
     * 批量添加应用到监控列表
     *
     * @param apps 应用列表
     */
    @Suppress("unused")
    fun addAppsToMonitor(apps: List<AppItem>) {
        viewModelScope.launch {
            repository.addAppsToMonitor(apps)
            Log.d(TAG, "批量添加应用到监控: ${apps.size}个")
        }
    }

    /**
     * 从监控列表移除应用
     *
     * @param packageName 应用包名
     */
    @Suppress("unused")
    fun removeAppFromMonitor(packageName: String) {
        viewModelScope.launch {
            repository.removeAppFromMonitor(packageName)
            Log.d(TAG, "从监控移除应用: $packageName")
        }
    }

    /**
     * 更新应用的时长阈值
     *
     * @param packageName 应用包名
     * @param thresholdSeconds 时长阈值（秒）
     */
    fun updateDurationThreshold(packageName: String, thresholdSeconds: Int) {
        viewModelScope.launch {
            repository.updateDurationThreshold(packageName, thresholdSeconds)
            Log.d(TAG, "更新时长阈值: $packageName -> ${thresholdSeconds}秒")
        }
    }

    /**
     * 清除错误信息
     */
    @Suppress("unused")
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    /**
     * 检查应用今日是否已打开
     *
     * @param packageName 应用包名
     * @return 是否已打开
     */
    @Suppress("unused")
    fun isAppOpenedToday(packageName: String): Boolean {
        return (_todayUsageMap.value[packageName] ?: 0) > 0
    }

    // ==================== 私有方法 ====================

    private fun getTodayDate(): Int = getCurrentDate()

    companion object {
        private const val TAG = "MainViewModel"

        fun getCurrentDate(): Int {
            val calendar = Calendar.getInstance()
            return calendar.get(Calendar.YEAR) * 10000 +
                    (calendar.get(Calendar.MONTH) + 1) * 100 +
                    calendar.get(Calendar.DAY_OF_MONTH)
        }
    }

    // ==================== 自定义打卡相关 ====================

    /**
     * 自定义打卡状态分组
     */
    val customCheckStatusGroup: StateFlow<CustomCheckStatusGroup> = combine(
        repository.getCustomCheckItemsFlow(),
        _currentDate.flatMapLatest { date ->
            repository.getCustomCheckRecordsByDateFlow(date)
        }
    ) { items, records ->
        val completedItemIds = records.filter { it.isCompleted }.map { it.itemId }.toSet()

        val statusList = items.map { item ->
            CustomCheckStatus(
                item = item,
                isCompleted = item.id in completedItemIds
            )
        }

        CustomCheckStatusGroup(
            incompleteItems = statusList.filter { !it.isCompleted },
            completedItems = statusList.filter { it.isCompleted }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CustomCheckStatusGroup()
    )

    /**
     * 添加自定义打卡项
     *
     * @param name 名称
     * @param iconType 图标类型
     * @param iconData 图标数据
     */
    fun addCustomCheckItem(name: String, iconType: String, iconData: String) {
        viewModelScope.launch {
            val item = CustomCheckItem(
                name = name,
                iconType = iconType,
                iconData = iconData
            )
            repository.addCustomCheckItem(item)
            Log.d(TAG, "添加自定义打卡项: $name")
        }
    }

    /**
     * 更新自定义打卡项
     *
     * @param item 打卡项
     */
    fun updateCustomCheckItem(item: CustomCheckItem) {
        viewModelScope.launch {
            repository.updateCustomCheckItem(item)
            Log.d(TAG, "更新自定义打卡项: ${item.id}")
        }
    }

    /**
     * 删除自定义打卡项
     *
     * @param id 打卡项ID
     */
    fun deleteCustomCheckItem(id: Int) {
        viewModelScope.launch {
            repository.deleteCustomCheckItem(id)
            Log.d(TAG, "删除自定义打卡项: $id")
        }
    }

    /**
     * 切换自定义打卡状态
     *
     * @param itemId 打卡项ID
     * @param isCompleted 是否完成
     */
    fun toggleCustomCheckStatus(itemId: Int, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.toggleCustomCheckStatus(itemId, isCompleted)
            Log.d(TAG, "切换自定义打卡状态: itemId=$itemId, isCompleted=$isCompleted")
        }
    }
}
