package com.dakala.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dakala.app.data.repository.AppUsageRepository
import com.dakala.app.data.repository.OrgTodoRepository
import com.dakala.app.domain.model.NotificationTime
import com.dakala.app.domain.model.WidgetOpacity
import com.dakala.app.widget.UsageWidgetProvider
import com.dakala.app.worker.NotificationWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页ViewModel
 *
 * 聚合展示与修改全部用户设置：通知时间、默认打卡时长、监控应用数、
 * Org 文件、小部件透明度。每项设置修改后立即生效并持久化到
 * app_settings 表；小部件透明度额外触发小部件重绘。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val repository: AppUsageRepository,
    private val orgRepository: OrgTodoRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    /**
     * 通知时间
     */
    private val _notificationTime = MutableStateFlow(NotificationTime.DEFAULT)
    val notificationTime: StateFlow<NotificationTime> = _notificationTime.asStateFlow()

    /**
     * 默认时长阈值（秒）
     */
    private val _defaultDurationThreshold = MutableStateFlow(600)
    val defaultDurationThreshold: StateFlow<Int> = _defaultDurationThreshold.asStateFlow()

    /**
     * 已选监控应用数量
     */
    val monitoredAppCount: StateFlow<Int> = repository.getMonitoredAppsFlow()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Org 文件可读路径；null 表示未设置
     */
    private val _orgFilePath = MutableStateFlow<String?>(null)
    val orgFilePath: StateFlow<String?> = _orgFilePath.asStateFlow()

    /**
     * 小部件背景不透明度
     */
    private val _widgetOpacity = MutableStateFlow(WidgetOpacity.DEFAULT)
    val widgetOpacity: StateFlow<WidgetOpacity> = _widgetOpacity.asStateFlow()

    /**
     * 小部件待办列表是否显示全部任务；false = 仅显示未完成
     */
    private val _widgetTodoShowAll = MutableStateFlow(false)
    val widgetTodoShowAll: StateFlow<Boolean> = _widgetTodoShowAll.asStateFlow()

    init {
        refresh()
    }

    /**
     * 重新加载各设置项当前值。进入设置页与返回本页（onResume）时调用。
     */
    fun refresh() {
        viewModelScope.launch {
            _notificationTime.value = repository.getNotificationTime()
            _defaultDurationThreshold.value = repository.getDefaultDurationThreshold()
            _orgFilePath.value = orgRepository.getOrgFilePath()
            _widgetOpacity.value = repository.getWidgetOpacity()
            _widgetTodoShowAll.value = repository.getWidgetTodoShowAll()
        }
    }

    /**
     * 设置通知时间并按新时间重排通知任务
     */
    fun setNotificationTime(time: NotificationTime) {
        viewModelScope.launch {
            repository.setNotificationTime(time)
            NotificationWorker.scheduleDailyNotification(getApplication(), time.hour, time.minute)
            _notificationTime.value = time
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
        }
    }

    /**
     * 设置 Org 待办文件并持久化访问权限
     */
    fun setOrgFile(uri: Uri) {
        viewModelScope.launch {
            orgRepository.setOrgFile(uri)
            _orgFilePath.value = orgRepository.getOrgFilePath()
        }
    }

    /**
     * 设置小部件背景不透明度并立即重绘所有小部件
     *
     * @param opacity 不透明度（百分比）
     */
    fun setWidgetOpacity(opacity: WidgetOpacity) {
        viewModelScope.launch {
            repository.setWidgetOpacity(opacity)
            _widgetOpacity.value = opacity
            UsageWidgetProvider.updateWidget(getApplication())
            Log.d(TAG, "小部件不透明度已设置为: ${opacity.percent}%")
        }
    }

    /**
     * 设置小部件待办列表显示范围并立即重绘所有小部件
     *
     * @param showAll true 显示全部任务（未完成在前）；false 仅显示未完成
     */
    fun setWidgetTodoShowAll(showAll: Boolean) {
        viewModelScope.launch {
            repository.setWidgetTodoShowAll(showAll)
            _widgetTodoShowAll.value = showAll
            UsageWidgetProvider.updateWidget(getApplication())
            Log.d(TAG, "小部件待办显示范围已设置为: showAll=$showAll")
        }
    }
}
