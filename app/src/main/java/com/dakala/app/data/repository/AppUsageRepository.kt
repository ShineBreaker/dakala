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
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

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
     * 获取所有应用（包括未监控的）
     * 
     * @return 所有应用列表
     */
    suspend fun getAllApps(): List<AppItem> {
        return appItemDao.getAllApps()
    }

    /**
     * 添加应用到监控列表
     * 
     * @param app 要添加的应用
     */
    suspend fun addAppToMonitor(app: AppItem) {
        Log.d(TAG, "添加应用到监控列表: ${app.packageName}")
        appItemDao.insertApp(app)
    }

    /**
     * 批量添加应用到监控列表
     * 
     * @param apps 要添加的应用列表
     */
    suspend fun addAppsToMonitor(apps: List<AppItem>) {
        Log.d(TAG, "批量添加应用到监控列表: ${apps.size}个应用")
        appItemDao.insertApps(apps)
    }

    /**
     * 从监控列表移除应用
     * 
     * @param packageName 要移除的应用包名
     */
    suspend fun removeAppFromMonitor(packageName: String) {
        Log.d(TAG, "从监控列表移除应用: $packageName")
        appItemDao.deleteByPackageName(packageName)
    }

    /**
     * 更新应用的监控状态
     * 
     * @param packageName 应用包名
     * @param isMonitored 是否监控
     */
    suspend fun updateMonitoredStatus(packageName: String, isMonitored: Boolean) {
        Log.d(TAG, "更新监控状态: $packageName -> $isMonitored")
        appItemDao.updateMonitoredStatus(packageName, isMonitored)
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

    /**
     * 根据包名获取应用
     * 
     * @param packageName 应用包名
     * @return 应用信息，不存在则返回null
     */
    suspend fun getAppByPackageName(packageName: String): AppItem? {
        return appItemDao.getAppByPackageName(packageName)
    }

    // ==================== 使用记录相关操作 ====================

    /**
     * 获取今日日期（格式：yyyyMMdd）
     * 
     * @return 今日日期整数
     */
    private fun getTodayDate(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.YEAR) * 10000 +
                (calendar.get(Calendar.MONTH) + 1) * 100 +
                calendar.get(Calendar.DAY_OF_MONTH)
    }

    /**
     * 更新应用使用记录
     * 
     * 将UsageStatsManager获取的使用时长保存到数据库。
     * 
     * @param packageName 应用包名
     * @param durationSeconds 使用时长（秒）
     */
    suspend fun updateUsageRecord(packageName: String, durationSeconds: Int) {
        val today = getTodayDate()
        val record = UsageRecord(
            packageName = packageName,
            date = today,
            durationSeconds = durationSeconds,
            lastUpdated = System.currentTimeMillis()
        )
        Log.d(TAG, "更新使用记录: $packageName, 今日时长: ${durationSeconds}秒")
        usageRecordDao.insertOrUpdateRecord(record)
    }

    /**
     * 批量更新应用使用记录
     * 
     * @param records 使用记录列表
     */
    suspend fun updateUsageRecords(records: List<UsageRecord>) {
        Log.d(TAG, "批量更新使用记录: ${records.size}条")
        usageRecordDao.insertOrUpdateRecords(records)
    }

    /**
     * 获取指定应用今日的使用记录
     * 
     * @param packageName 应用包名
     * @return 使用记录，不存在则返回null
     */
    suspend fun getTodayUsageRecord(packageName: String): UsageRecord? {
        return usageRecordDao.getRecord(packageName, getTodayDate())
    }

    /**
     * 获取今日所有应用的使用记录
     * 
     * @return 今日使用记录列表
     */
    suspend fun getTodayUsageRecords(): List<UsageRecord> {
        return usageRecordDao.getRecordsByDate(getTodayDate())
    }

    /**
     * 清理旧的使用记录（保留最近7天）
     */
    suspend fun cleanOldRecords() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -7)
        val beforeDate = calendar.get(Calendar.YEAR) * 10000 +
                (calendar.get(Calendar.MONTH) + 1) * 100 +
                calendar.get(Calendar.DAY_OF_MONTH)
        Log.d(TAG, "清理旧记录: $beforeDate 之前")
        usageRecordDao.deleteOldRecords(beforeDate)
    }

    // ==================== 应用设置相关操作 ====================

    /**
     * 获取通知时间设置
     * 
     * @return 通知时间（格式：HH:mm），默认为22:00
     */
    suspend fun getNotificationTime(): String {
        return appSettingDao.getSetting(AppSetting.KEY_NOTIFICATION_TIME) ?: "22:00"
    }

    /**
     * 设置通知时间
     * 
     * @param time 通知时间（格式：HH:mm）
     */
    suspend fun setNotificationTime(time: String) {
        Log.d(TAG, "设置通知时间: $time")
        appSettingDao.setSetting(AppSetting(AppSetting.KEY_NOTIFICATION_TIME, time))
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
     * 获取通知时间设置（响应式）
     * 
     * @return 通知时间的Flow
     */
    fun getNotificationTimeFlow(): Flow<String?> {
        return appSettingDao.getSettingFlow(AppSetting.KEY_NOTIFICATION_TIME)
    }

    // ==================== 自定义打卡项相关操作 ====================

    /**
     * 获取所有自定义打卡项（响应式）
     */
    fun getCustomCheckItemsFlow(): Flow<List<CustomCheckItem>> {
        return customCheckItemDao.getAllItemsFlow()
    }

    /**
     * 获取所有自定义打卡项
     */
    suspend fun getCustomCheckItems(): List<CustomCheckItem> {
        return customCheckItemDao.getAllItems()
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
     * 获取今日自定义打卡记录（响应式）
     */
    fun getTodayCustomCheckRecordsFlow(): Flow<List<CustomCheckRecord>> {
        return customCheckRecordDao.getRecordsByDateFlow(getTodayDate())
    }

    /**
     * 获取今日自定义打卡记录
     */
    suspend fun getTodayCustomCheckRecords(): List<CustomCheckRecord> {
        return customCheckRecordDao.getRecordsByDate(getTodayDate())
    }

    /**
     * 切换自定义打卡状态
     */
    suspend fun toggleCustomCheckStatus(itemId: Int, isCompleted: Boolean) {
        val today = getTodayDate()
        val record = CustomCheckRecord(
            itemId = itemId,
            date = today,
            isCompleted = isCompleted,
            completedAt = if (isCompleted) System.currentTimeMillis() else null
        )
        Log.d(TAG, "切换自定义打卡状态: itemId=$itemId, isCompleted=$isCompleted")
        customCheckRecordDao.insertOrUpdateRecord(record)
    }

    /**
     * 检查今日是否已完成自定义打卡
     */
    suspend fun isCustomCheckCompletedToday(itemId: Int): Boolean {
        val record = customCheckRecordDao.getRecord(itemId, getTodayDate())
        return record?.isCompleted ?: false
    }
}