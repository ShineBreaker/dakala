package com.dakala.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 应用监控项实体类
 * 
 * 用于存储用户选择要监控的应用信息。
 * 每个记录代表一个被监控的应用，包含其包名、显示名称和监控状态。
 * 
 * @property id 主键，自动生成
 * @property packageName 应用包名，唯一标识一个应用
 * @property appName 应用显示名称，用于UI展示
 * @property isMonitored 是否正在监控此应用
 * @property durationThreshold 时长阈值（秒），低于此值视为未完成打卡
 * @property createdAt 创建时间戳
 * @property updatedAt 最后更新时间戳
 */
@Entity(tableName = "app_items")
data class AppItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val packageName: String,
    val appName: String,
    val isMonitored: Boolean = true,
    val durationThreshold: Int = 600, // 默认10分钟（600秒）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 应用使用记录实体类
 * 
 * 用于存储每日的应用使用记录，便于历史查询和统计。
 * 
 * @property id 主键，自动生成
 * @property packageName 应用包名
 * @property date 日期（格式：yyyyMMdd）
 * @property durationSeconds 使用时长（秒）
 * @property lastUpdated 最后更新时间戳
 */
@Entity(tableName = "usage_records", primaryKeys = ["packageName", "date"])
data class UsageRecord(
    val packageName: String,
    val date: Int,
    val durationSeconds: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * 应用设置实体类
 * 
 * 存储应用的全局设置，如通知时间等。
 * 
 * @property key 设置键名
 * @property value 设置值
 */
@Entity(tableName = "app_settings", primaryKeys = ["key"])
data class AppSetting(
    val key: String,
    val value: String
) {
    companion object {
        const val KEY_NOTIFICATION_TIME = "notification_time"
        const val KEY_DEFAULT_DURATION_THRESHOLD = "default_duration_threshold"
    }
}

/**
 * 自定义打卡项实体类
 *
 * 用于存储用户自定义的打卡项目。
 * 每个记录代表一个自定义打卡项，包含图标和名称。
 *
 * @property id 主键，自动生成
 * @property name 打卡项名称
 * @property iconType 图标类型（emoji 或 image）
 * @property iconData 图标数据（emoji字符或图片URI字符串）
 * @property createdAt 创建时间戳
 * @property updatedAt 最后更新时间戳
 */
@Entity(tableName = "custom_check_items")
data class CustomCheckItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val iconType: String = "emoji", // "emoji" 或 "image"
    val iconData: String = "✅", // emoji字符或图片URI
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 自定义打卡记录实体类
 *
 * 用于存储每日的自定义打卡记录。
 *
 * @property itemId 自定义打卡项ID
 * @property date 日期（格式：yyyyMMdd）
 * @property isCompleted 是否已完成
 * @property completedAt 完成时间戳
 */
@Entity(tableName = "custom_check_records", primaryKeys = ["itemId", "date"])
data class CustomCheckRecord(
    val itemId: Int,
    val date: Int,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null
)