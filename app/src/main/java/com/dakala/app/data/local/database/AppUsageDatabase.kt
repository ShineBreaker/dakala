package com.dakala.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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

/**
 * 应用使用统计数据库
 *
 * Room数据库主类，管理应用监控项、使用记录和应用设置的存储。
 * 使用单例模式确保全局只有一个数据库实例。
 *
 * 包含以下表：
 * - app_items: 存储用户选择监控的应用
 * - usage_records: 存储每日应用使用记录
 * - app_settings: 存储应用全局设置
 * - custom_check_items: 存储自定义打卡项
 * - custom_check_records: 存储自定义打卡记录
 */
@Database(
    entities = [
        AppItem::class,
        UsageRecord::class,
        AppSetting::class,
        CustomCheckItem::class,
        CustomCheckRecord::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppUsageDatabase : RoomDatabase() {

    /**
     * 获取应用监控项DAO
     */
    abstract fun appItemDao(): AppItemDao

    /**
     * 获取使用记录DAO
     */
    abstract fun usageRecordDao(): UsageRecordDao

    /**
     * 获取应用设置DAO
     */
    abstract fun appSettingDao(): AppSettingDao

    /**
     * 获取自定义打卡项DAO
     */
    abstract fun customCheckItemDao(): CustomCheckItemDao

    /**
     * 获取自定义打卡记录DAO
     */
    abstract fun customCheckRecordDao(): CustomCheckRecordDao

    companion object {
        private const val DATABASE_NAME = "dakala_database"

        @Volatile
        private var INSTANCE: AppUsageDatabase? = null

        /**
         * 获取数据库实例
         * 
         * 使用双重检查锁定模式确保线程安全的单例实现。
         * 
         * @param context 应用上下文
         * @return 数据库实例
         */
        fun getInstance(context: Context): AppUsageDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppUsageDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppUsageDatabase::class.java,
                DATABASE_NAME
            )
                // 允许主线程查询（仅用于简单查询，生产环境应避免）
                .allowMainThreadQueries()
                // 数据库迁移策略（版本升级时使用）
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }

        /**
         * 清除数据库实例（用于测试）
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }
}
