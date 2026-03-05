package com.dakala.app.data.local.dao

import androidx.room.*
import com.dakala.app.data.local.entity.AppItem
import com.dakala.app.data.local.entity.UsageRecord
import com.dakala.app.data.local.entity.AppSetting
import kotlinx.coroutines.flow.Flow

/**
 * 应用监控项数据访问对象
 * 
 * 提供对app_items表的CRUD操作。
 * 使用Flow实现响应式数据更新，当数据变化时自动通知UI层。
 */
@Dao
interface AppItemDao {

    /**
     * 插入一个应用监控项
     * 如果已存在相同packageName的记录，则替换
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: AppItem)

    /**
     * 批量插入应用监控项
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<AppItem>)

    /**
     * 更新应用监控项
     */
    @Update
    suspend fun updateApp(app: AppItem)

    /**
     * 删除应用监控项
     */
    @Delete
    suspend fun deleteApp(app: AppItem)

    /**
     * 根据包名删除应用监控项
     */
    @Query("DELETE FROM app_items WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    /**
     * 获取所有被监控的应用
     * 返回Flow，实现响应式更新
     */
    @Query("SELECT * FROM app_items WHERE isMonitored = 1 ORDER BY appName ASC")
    fun getMonitoredAppsFlow(): Flow<List<AppItem>>

    /**
     * 获取所有被监控的应用（一次性查询）
     */
    @Query("SELECT * FROM app_items WHERE isMonitored = 1 ORDER BY appName ASC")
    suspend fun getMonitoredApps(): List<AppItem>

    /**
     * 获取所有应用（包括未监控的）
     */
    @Query("SELECT * FROM app_items ORDER BY appName ASC")
    suspend fun getAllApps(): List<AppItem>

    /**
     * 根据包名获取应用
     */
    @Query("SELECT * FROM app_items WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppByPackageName(packageName: String): AppItem?

    /**
     * 更新应用的监控状态
     */
    @Query("UPDATE app_items SET isMonitored = :isMonitored, updatedAt = :timestamp WHERE packageName = :packageName")
    suspend fun updateMonitoredStatus(packageName: String, isMonitored: Boolean, timestamp: Long = System.currentTimeMillis())

    /**
     * 更新应用的时长阈值
     */
    @Query("UPDATE app_items SET durationThreshold = :threshold, updatedAt = :timestamp WHERE packageName = :packageName")
    suspend fun updateDurationThreshold(packageName: String, threshold: Int, timestamp: Long = System.currentTimeMillis())

    /**
     * 清空所有应用监控项
     */
    @Query("DELETE FROM app_items")
    suspend fun deleteAll()
}

/**
 * 使用记录数据访问对象
 * 
 * 提供对usage_records表的CRUD操作。
 */
@Dao
interface UsageRecordDao {

    /**
     * 插入或更新使用记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRecord(record: UsageRecord)

    /**
     * 批量插入或更新使用记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRecords(records: List<UsageRecord>)

    /**
     * 获取指定应用在指定日期的使用记录
     * @param packageName 应用包名
     * @param date 日期（格式：yyyyMMdd）
     */
    @Query("SELECT * FROM usage_records WHERE packageName = :packageName AND date = :date LIMIT 1")
    suspend fun getRecord(packageName: String, date: Int): UsageRecord?

    /**
     * 获取指定日期所有应用的使用记录
     */
    @Query("SELECT * FROM usage_records WHERE date = :date")
    suspend fun getRecordsByDate(date: Int): List<UsageRecord>

    /**
     * 获取指定应用的使用记录（响应式）
     */
    @Query("SELECT * FROM usage_records WHERE packageName = :packageName AND date = :date")
    fun getRecordFlow(packageName: String, date: Int): Flow<UsageRecord?>

    /**
     * 删除指定日期之前的记录（清理旧数据）
     */
    @Query("DELETE FROM usage_records WHERE date < :beforeDate")
    suspend fun deleteOldRecords(beforeDate: Int)
}

/**
 * 应用设置数据访问对象
 * 
 * 提供对app_settings表的CRUD操作。
 */
@Dao
interface AppSettingDao {

    /**
     * 插入或更新设置
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: AppSetting)

    /**
     * 获取设置值
     */
    @Query("SELECT value FROM app_settings WHERE key = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    /**
     * 获取设置值（响应式）
     */
    @Query("SELECT value FROM app_settings WHERE key = :key LIMIT 1")
    fun getSettingFlow(key: String): Flow<String?>

    /**
     * 删除设置
     */
    @Query("DELETE FROM app_settings WHERE key = :key")
    suspend fun deleteSetting(key: String)
}