package com.dakala.app.domain.model

import com.dakala.app.data.local.entity.AppItem
import com.dakala.app.data.local.entity.CustomCheckItem

/**
 * 应用监控状态UI模型
 * 
 * 用于在UI层展示应用的监控状态，包含今日使用情况。
 * 
 * @property appItem 原始应用数据
 * @property todayDurationSeconds 今日使用时长（秒）
 * @property isCompleted 今日是否已完成打卡目标
 */
data class AppMonitorStatus(
    val appItem: AppItem,
    val todayDurationSeconds: Int = 0
) {
    /**
     * 今日是否已打开过
     */
    val isOpenedToday: Boolean
        get() = todayDurationSeconds > 0

    /**
     * 今日是否已完成打卡目标
     */
    val isCompleted: Boolean
        get() = todayDurationSeconds >= appItem.durationThreshold

    /**
     * 今日使用时长（分钟）
     */
    val todayDurationMinutes: Int
        get() = todayDurationSeconds / 60

    /**
     * 今日使用时长（格式化字符串）
     */
    val todayDurationFormatted: String
        get() = formatDuration(todayDurationSeconds)

    /**
     * 时长阈值（分钟）
     */
    val thresholdMinutes: Int
        get() = appItem.durationThreshold / 60

    /**
     * 时长阈值（格式化字符串）
     */
    val thresholdFormatted: String
        get() = formatDuration(appItem.durationThreshold)

    /**
     * 剩余需要使用的时长（秒）
     */
    val remainingSeconds: Int
        get() = maxOf(0, appItem.durationThreshold - todayDurationSeconds)

    /**
     * 完成进度（0-100）
     */
    val progress: Float
        get() = if (appItem.durationThreshold > 0) {
            (todayDurationSeconds.toFloat() / appItem.durationThreshold).coerceIn(0f, 1f)
        } else {
            1f
        }

    companion object {
        /**
         * 格式化时长显示
         * 
         * @param seconds 秒数
         * @return 格式化后的字符串（如：10分钟30秒）
         */
        fun formatDuration(seconds: Int): String {
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            return when {
                minutes > 0 && remainingSeconds > 0 -> "${minutes}分钟${remainingSeconds}秒"
                minutes > 0 -> "${minutes}分钟"
                else -> "${remainingSeconds}秒"
            }
        }
    }
}

/**
 * 监控状态分组
 * 
 * 将应用按完成状态分组，便于UI展示。
 * 
 * @property incompleteApps 未完成的应用列表
 * @property completedApps 已完成的应用列表
 */
data class MonitorStatusGroup(
    val incompleteApps: List<AppMonitorStatus> = emptyList(),
    val completedApps: List<AppMonitorStatus> = emptyList()
) {
    /**
     * 是否有未完成的应用
     */
    val hasIncompleteApps: Boolean
        get() = incompleteApps.isNotEmpty()

    /**
     * 是否有已完成的应用
     */
    val hasCompletedApps: Boolean
        get() = completedApps.isNotEmpty()

    /**
     * 是否为空（没有任何监控应用）
     */
    val isEmpty: Boolean
        get() = incompleteApps.isEmpty() && completedApps.isEmpty()

    /**
     * 总应用数
     */
    val totalApps: Int
        get() = incompleteApps.size + completedApps.size

    /**
     * 已完成应用数
     */
    val completedCount: Int
        get() = completedApps.size

    /**
     * 完成率（0-100）
     */
    val completionRate: Float
        get() = if (totalApps > 0) {
            completedCount.toFloat() / totalApps
        } else {
            0f
        }
}

/**
 * 自定义打卡状态UI模型
 *
 * 用于在UI层展示自定义打卡项的状态。
 *
 * @property item 自定义打卡项
 * @property isCompleted 今日是否已完成
 */
data class CustomCheckStatus(
    val item: CustomCheckItem,
    val isCompleted: Boolean = false
)

/**
 * 自定义打卡状态分组
 *
 * 将自定义打卡项按完成状态分组，便于UI展示。
 *
 * @property incompleteItems 未完成的打卡项列表
 * @property completedItems 已完成的打卡项列表
 */
data class CustomCheckStatusGroup(
    val incompleteItems: List<CustomCheckStatus> = emptyList(),
    val completedItems: List<CustomCheckStatus> = emptyList()
) {
    /**
     * 是否有未完成的打卡项
     */
    val hasIncompleteItems: Boolean
        get() = incompleteItems.isNotEmpty()

    /**
     * 是否有已完成的打卡项
     */
    val hasCompletedItems: Boolean
        get() = completedItems.isNotEmpty()

    /**
     * 是否为空（没有任何打卡项）
     */
    val isEmpty: Boolean
        get() = incompleteItems.isEmpty() && completedItems.isEmpty()

    /**
     * 总打卡项数
     */
    val totalItems: Int
        get() = incompleteItems.size + completedItems.size

    /**
     * 已完成打卡项数
     */
    val completedCount: Int
        get() = completedItems.size

    /**
     * 完成率（0-100）
     */
    val completionRate: Float
        get() = if (totalItems > 0) {
            completedCount.toFloat() / totalItems
        } else {
            0f
        }
}