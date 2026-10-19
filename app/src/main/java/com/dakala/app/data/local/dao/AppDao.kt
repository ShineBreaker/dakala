package com.dakala.app.data.local.dao

import androidx.room.*
import com.dakala.app.data.local.entity.AppItem
import com.dakala.app.data.local.entity.UsageRecord
import com.dakala.app.data.local.entity.AppSetting
import com.dakala.app.data.local.entity.CustomCheckItem
import com.dakala.app.data.local.entity.CustomCheckRecord
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
     * 更新应用的时长阈值
     */
    @Query("UPDATE app_items SET durationThreshold = :threshold, updatedAt = :timestamp WHERE packageName = :packageName")
    suspend fun updateDurationThreshold(packageName: String, threshold: Int, timestamp: Long = System.currentTimeMillis())

    /**
     * 事务：以传入列表为准同步监控列表
     *
     * 不在列表内的监控项被移除；列表中新增的项被插入。已监控的项保持原样，
     * 不覆盖用户改过的时长阈值。整批要么全部生效、要么整体回滚。
     */
    @Transaction
    suspend fun syncMonitoredApps(selected: List<AppItem>) {
        val current = getMonitoredApps()
        val currentNames = current.map { it.packageName }.toSet()
        val selectedNames = selected.map { it.packageName }.toSet()
        for (app in current) {
            if (app.packageName !in selectedNames) {
                deleteByPackageName(app.packageName)
            }
        }
        for (app in selected) {
            if (app.packageName !in currentNames) {
                insertApp(app)
            }
        }
    }
}

/**
 * 使用记录数据访问对象
 * 
 * 提供对usage_records表的CRUD操作。
 */
@Dao
interface UsageRecordDao {

    /**
     * 批量插入或更新使用记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRecords(records: List<UsageRecord>)
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
     * 删除设置
     */
    @Query("DELETE FROM app_settings WHERE key = :key")
    suspend fun deleteSetting(key: String)
}

/**
 * 自定义打卡项数据访问对象
 *
 * 提供对custom_check_items表的CRUD操作。
 */
@Dao
interface CustomCheckItemDao {

    /**
     * 插入一个自定义打卡项
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: CustomCheckItem): Long

    /**
     * 更新自定义打卡项
     */
    @Update
    suspend fun updateItem(item: CustomCheckItem)

    /**
     * 根据ID删除自定义打卡项
     */
    @Query("DELETE FROM custom_check_items WHERE id = :id")
    suspend fun deleteById(id: Int)

    /**
     * 获取所有自定义打卡项
     */
    @Query("SELECT * FROM custom_check_items ORDER BY createdAt ASC")
    suspend fun getAllItems(): List<CustomCheckItem>

    /**
     * 获取所有自定义打卡项（响应式）
     */
    @Query("SELECT * FROM custom_check_items ORDER BY createdAt ASC")
    fun getAllItemsFlow(): Flow<List<CustomCheckItem>>
}

/**
 * 自定义打卡记录数据访问对象
 *
 * 提供对custom_check_records表的CRUD操作。
 */
@Dao
interface CustomCheckRecordDao {

    /**
     * 插入或更新打卡记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRecord(record: CustomCheckRecord)

    /**
     * 获取指定日期所有打卡记录
     */
    @Query("SELECT * FROM custom_check_records WHERE date = :date")
    suspend fun getRecordsByDate(date: Int): List<CustomCheckRecord>

    /**
     * 获取指定日期所有打卡记录（响应式）
     */
    @Query("SELECT * FROM custom_check_records WHERE date = :date")
    fun getRecordsByDateFlow(date: Int): Flow<List<CustomCheckRecord>>
}