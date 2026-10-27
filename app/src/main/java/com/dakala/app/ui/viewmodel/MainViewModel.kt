package com.dakala.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dakala.app.data.local.entity.AppItem
import com.dakala.app.data.local.entity.CustomCheckItem
import com.dakala.app.data.repository.AppUsageRepository
import com.dakala.app.domain.model.AppMonitorStatus
import com.dakala.app.domain.model.CustomCheckStatus
import com.dakala.app.domain.model.DayResetTime
import com.dakala.app.domain.model.StatusGroup
import com.dakala.app.domain.usecase.AppUsageQuery
import com.dakala.app.domain.usecase.UsageStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
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
     * 上次刷新时各应用打卡周期戳的快照（重置点+日期键），用于检测跨周期
     */
    private var lastRefreshStamp: String = ""

    /**
     * 全局默认重置时间（响应式），驱动应用卡片的重置时间展示
     */
    private val _defaultResetTime = MutableStateFlow(DayResetTime.DEFAULT)
    val defaultResetTime: StateFlow<DayResetTime> = _defaultResetTime.asStateFlow()

    /**
     * 今日使用时长映射（包名 -> 秒数），驱动监控状态分组
     */
    private val _todayUsageMap = MutableStateFlow<Map<String, Int>>(emptyMap())

    /**
     * 当前打卡日期键（响应式），用于驱动自定义打卡记录的日期查询
     */
    private val _currentDate = MutableStateFlow(
        DayResetTime.DEFAULT.dateKey(Clock.systemDefaultZone())
    )

    /**
     * 监控状态分组（按完成状态分组）
     */
    val monitorStatusGroup: StateFlow<StatusGroup<AppMonitorStatus>> = combine(
        repository.getMonitoredAppsFlow(),
        _todayUsageMap
    ) { apps, usageMap ->
        val statusList = apps.map { appItem ->
            AppMonitorStatus(
                appItem = appItem,
                todayDurationSeconds = usageMap[appItem.packageName] ?: 0
            )
        }

        StatusGroup(
            incomplete = statusList.filter { !it.isCompleted },
            completed = statusList.filter { it.isCompleted }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatusGroup()
    )

    /**
     * 加载状态
     */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ==================== 初始化 ====================

    init {
        refreshUsageStats()
    }

    /**
     * 检测打卡周期变更并刷新数据。
     * 应在 Activity.onResume 时调用。
     *
     * 翻篇时刻按每个应用的重置点计算（不再只看自然日零点）；
     * 重置时间本身被修改也会使快照失配，从而触发刷新。
     */
    fun checkDateAndRefresh() {
        viewModelScope.launch {
            val clock = Clock.systemDefaultZone()
            val defaultReset = repository.getDefaultResetTime()
            val monitoredApps = repository.getMonitoredApps()
            val stamp = periodStamp(monitoredApps, defaultReset, clock)

            if (stamp != lastRefreshStamp) {
                Log.d(TAG, "检测到打卡周期变更")
                _currentDate.value = defaultReset.dateKey(clock)
                refreshUsageStats(forceReset = true)
            }
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
                // 获取所有被监控的应用与全局默认重置时间
                val monitoredApps = repository.getMonitoredApps()
                val defaultReset = repository.getDefaultResetTime()
                _defaultResetTime.value = defaultReset

                val clock = Clock.systemDefaultZone()
                lastRefreshStamp = periodStamp(monitoredApps, defaultReset, clock)
                _currentDate.value = defaultReset.dateKey(clock)

                if (monitoredApps.isEmpty()) {
                    _todayUsageMap.value = emptyMap()
                    _isLoading.value = false
                    return@launch
                }

                // 强制重置时先清空数据，确保 StateFlow 必然触发 UI 更新
                if (forceReset) {
                    _todayUsageMap.value = emptyMap()
                }

                // 批量获取各应用打卡周期内使用时长（binder 查询移出主线程）
                val usageMap = withContext(Dispatchers.IO) {
                    usageStatsUseCase.getAppsUsageDuration(
                        monitoredApps.map {
                            AppUsageQuery(
                                it.packageName,
                                it.resetTimeMinutes ?: defaultReset.minutesOfDay
                            )
                        }
                    )
                }

                // 更新数据库记录（日期键取各应用自己的打卡周期）
                val records = monitoredApps.map { app ->
                    val reset = DayResetTime.ofMinutes(
                        app.resetTimeMinutes ?: defaultReset.minutesOfDay
                    )
                    com.dakala.app.data.local.entity.UsageRecord(
                        packageName = app.packageName,
                        date = reset.dateKey(clock),
                        durationSeconds = usageMap[app.packageName] ?: 0
                    )
                }
                repository.updateUsageRecords(records)

                // 更新UI状态：创建新Map实例，即使内容相同也触发StateFlow
                _todayUsageMap.value = usageMap.toMap()

                Log.d(TAG, "刷新使用统计完成: ${usageMap.size}个应用")
            } catch (e: Exception) {
                Log.e(TAG, "刷新使用统计失败", e)
            } finally {
                _isLoading.value = false
            }
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
     * 更新应用的每日重置时间
     *
     * 写库后立即重算使用时长：周期边界变了，原周期内的累计时长不再适用。
     *
     * @param packageName 应用包名
     * @param resetTime 重置时间；null 表示恢复为跟随全局默认
     */
    fun updateResetTime(packageName: String, resetTime: DayResetTime?) {
        viewModelScope.launch {
            repository.updateResetTime(packageName, resetTime)
            Log.d(TAG, "更新重置时间: $packageName -> ${resetTime?.format() ?: "跟随全局"}")
            refreshUsageStats()
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 各应用打卡周期快照：重置点 + 当前周期日期键
     *
     * 周期翻篇（跨重置点）与重置时间被修改都会产生不同的快照。
     */
    private fun periodStamp(
        apps: List<AppItem>,
        defaultReset: DayResetTime,
        clock: Clock
    ): String = buildString {
        append(defaultReset.minutesOfDay)
        for (app in apps) {
            val reset = DayResetTime.ofMinutes(
                app.resetTimeMinutes ?: defaultReset.minutesOfDay
            )
            append('|').append(app.packageName)
                .append(':').append(reset.minutesOfDay)
                .append('@').append(reset.dateKey(clock))
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
    }

    // ==================== 自定义打卡相关 ====================

    /**
     * 自定义打卡状态分组
     */
    val customCheckStatusGroup: StateFlow<StatusGroup<CustomCheckStatus>> = combine(
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

        StatusGroup(
            incomplete = statusList.filter { !it.isCompleted },
            completed = statusList.filter { it.isCompleted }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatusGroup()
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
