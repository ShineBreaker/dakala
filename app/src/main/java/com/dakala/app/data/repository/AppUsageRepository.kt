package com.dakala.app.data.repository

import android.util.Log
import com.dakala.app.data.local.dao.AppItemDao
import com.dakala.app.data.local.dao.UsageRecordDao
import com.dakala.app.data.local.dao.AppSettingDao
import com.dakala.app.data.local.dao.CustomCheckItemDao
import com.dakala.app.data.local.dao.CustomCheckRecordDao
import com.dakala.app.data.local.entity.AppItem
import com.dakala.app.data.local.entity.UsageRecord
import com.dakala.app.data.local.entity.AppSetting
import com.dakala.app.data.local.entity.CustomCheckItem
import com.dakala.app.data.local.entity.CustomCheckRecord
import com.dakala.app.domain.model.DayStamp
import com.dakala.app.domain.model.NotificationTime
import com.dakala.app.domain.model.WidgetOpacity
import kotlinx.coroutines.flow.Flow

/**
 * 应用使用统计仓库
 *
 * 数据层的核心类，负责协调数据源（数据库）和业务逻辑层之间的数据交互。
 * 封装了所有数据访问逻辑，为ViewModel提供统一的数据接口。
 *
 * 主要职责：
 * 1. 管理应用监控项的增删改查
 * 2. 管理使用记录的存储和查询
 * 3. 管理应用设置的读写
 * 4. 管理自定义打卡项和记录
 *
 * @property appItemDao 应用监控项DAO
 * @property usageRecordDao 使用记录DAO
 * @property appSettingDao 应用设置DAO
 * @property customCheckItemDao 自定义打卡项DAO
 * @property customCheckRecordDao 自定义打卡记录DAO
 */
class AppUsageRepository(
    private val appItemDao: AppItemDao,
    private val usageRecordDao: UsageRecordDao,
    private val appSettingDao: AppSettingDao,
    private val customCheckItemDao: CustomCheckItemDao,
    private val customCheckRecordDao: CustomCheckRecordDao
) {
    companion object {
        private const val TAG = "AppUsageRepository"
    }

    // ==================== 应用监控项相关操作 ====================

    /**
     * 获取所有被监控的应用（响应式）
     * 
     * 返回Flow，当数据库中的数据发生变化时，会自动发出新的数据流。
     * ViewModel可以订阅此Flow来实时更新UI。
     * 
     * @return 被监控应用列表的Flow
     */
    fun getMonitoredAppsFlow(): Flow<List<AppItem>> {
        return appItemDao.getMonitoredAppsFlow()
    }

    /**
     * 获取所有被监控的应用（一次性查询）
     * 
     * @return 被监控应用列表
     */
    suspend fun getMonitoredApps(): List<AppItem> {
        return appItemDao.getMonitoredApps()
    }

    /**
     * 以传入列表为准同步监控列表（事务）
     *
     * 不在列表内的监控项被移除、新增的项被插入，整批原子生效；
     * 保存入口在界面 finish() 之后也不能被截断成「只保存一半」。
     *
     * @param selected 应保留的监控应用列表
     */
    suspend fun syncMonitoredApps(selected: List<AppItem>) {
        return appItemDao.syncMonitoredApps(selected)
    }

    /**
     * 更新应用的时长阈值
     *
     * @param packageName 应用包名
     * @param threshold 时长阈值（秒）
     */
    suspend fun updateDurationThreshold(packageName: String, threshold: Int) {
        Log.d(TAG, "更新时长阈值: $packageName -> ${threshold}秒")
        appItemDao.updateDurationThreshold(packageName, threshold)
    }

    // ==================== 使用记录相关操作 ====================

    /**
     * 批量更新应用使用记录
     *
     * @param records 使用记录列表
     */
    suspend fun updateUsageRecords(records: List<UsageRecord>) {
        Log.d(TAG, "批量更新使用记录: ${records.size}条")
        usageRecordDao.insertOrUpdateRecords(records)
    }

    // ==================== 应用设置相关操作 ====================

    /**
     * 获取通知时间设置
     *
     * 存储格式（"HH:mm" 字符串）与默认值 22:00 的回退都在 NotificationTime 内处理，
     * 调用方不接触原始字符串。
     */
    suspend fun getNotificationTime(): NotificationTime {
        return NotificationTime.parse(appSettingDao.getSetting(AppSetting.KEY_NOTIFICATION_TIME))
    }

    /**
     * 设置通知时间
     *
     * @param time 通知时间
     */
    suspend fun setNotificationTime(time: NotificationTime) {
        Log.d(TAG, "设置通知时间: ${time.format()}")
        appSettingDao.setSetting(AppSetting(AppSetting.KEY_NOTIFICATION_TIME, time.format()))
    }

    /**
     * 获取默认时长阈值
     * 
     * @return 默认时长阈值（秒），默认为600秒（10分钟）
     */
    suspend fun getDefaultDurationThreshold(): Int {
        val value = appSettingDao.getSetting(AppSetting.KEY_DEFAULT_DURATION_THRESHOLD)
        return value?.toIntOrNull() ?: 600
    }

    /**
     * 设置默认时长阈值
     * 
     * @param threshold 时长阈值（秒）
     */
    suspend fun setDefaultDurationThreshold(threshold: Int) {
        Log.d(TAG, "设置默认时长阈值: $threshold 秒")
        appSettingDao.setSetting(AppSetting(AppSetting.KEY_DEFAULT_DURATION_THRESHOLD, threshold.toString()))
    }

    /**
     * 获取小部件背景不透明度
     *
     * 存储格式（百分比字符串）与默认值回退都在 WidgetOpacity 内处理。
     */
    suspend fun getWidgetOpacity(): WidgetOpacity {
        return WidgetOpacity.parse(appSettingDao.getSetting(AppSetting.KEY_WIDGET_OPACITY))
    }

    /**
     * 设置小部件背景不透明度
     *
     * @param opacity 不透明度（百分比）
     */
    suspend fun setWidgetOpacity(opacity: WidgetOpacity) {
        Log.d(TAG, "设置小部件不透明度: ${opacity.percent}%")
        appSettingDao.setSetting(AppSetting(AppSetting.KEY_WIDGET_OPACITY, opacity.format()))
    }

    /**
     * 小部件待办列表是否显示全部任务；缺省 false = 仅显示未完成
     */
    suspend fun getWidgetTodoShowAll(): Boolean {
        return appSettingDao.getSetting(AppSetting.KEY_WIDGET_TODO_SHOW_ALL).toBoolean()
    }

    /**
     * 设置小部件待办列表显示范围
     */
    suspend fun setWidgetTodoShowAll(showAll: Boolean) {
        Log.d(TAG, "设置小部件待办显示范围: showAll=$showAll")
        appSettingDao.setSetting(AppSetting(AppSetting.KEY_WIDGET_TODO_SHOW_ALL, showAll.toString()))
    }

    // ==================== 自定义打卡项相关操作 ====================

    /**
     * 获取所有自定义打卡项（响应式）
     */
    fun getCustomCheckItemsFlow(): Flow<List<CustomCheckItem>> {
        return customCheckItemDao.getAllItemsFlow()
    }

    /**
     * 添加自定义打卡项
     */
    suspend fun addCustomCheckItem(item: CustomCheckItem): Long {
        Log.d(TAG, "添加自定义打卡项: ${item.name}")
        return customCheckItemDao.insertItem(item)
    }

    /**
     * 更新自定义打卡项
     */
    suspend fun updateCustomCheckItem(item: CustomCheckItem) {
        Log.d(TAG, "更新自定义打卡项: ${item.id}")
        customCheckItemDao.updateItem(item)
    }

    /**
     * 删除自定义打卡项
     */
    suspend fun deleteCustomCheckItem(id: Int) {
        Log.d(TAG, "删除自定义打卡项: $id")
        customCheckItemDao.deleteById(id)
    }

    /**
     * 获取指定日期自定义打卡记录（响应式）
     */
    fun getCustomCheckRecordsByDateFlow(date: Int): Flow<List<CustomCheckRecord>> {
        return customCheckRecordDao.getRecordsByDateFlow(date)
    }

    /**
     * 切换自定义打卡状态
     */
    suspend fun toggleCustomCheckStatus(itemId: Int, isCompleted: Boolean) {
        val today = DayStamp.today()
        val record = CustomCheckRecord(
            itemId = itemId,
            date = today,
            isCompleted = isCompleted,
            completedAt = if (isCompleted) System.currentTimeMillis() else null
        )
        Log.d(TAG, "切换自定义打卡状态: itemId=$itemId, isCompleted=$isCompleted")
        customCheckRecordDao.insertOrUpdateRecord(record)
    }
}