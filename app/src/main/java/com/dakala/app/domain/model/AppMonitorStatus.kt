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
     * 今日使用时长（格式化字符串）
     */
    val todayDurationFormatted: String
        get() = formatDuration(todayDurationSeconds)

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
 * 将受监控应用与今日使用时长映射为监控状态列表
 *
 * 供 UI、桌面小部件与通知路径共用同一份完成判定，避免各自重复实现。
 * 输出顺序与 [apps] 一致。
 *
 * @param apps 受监控的应用列表
 * @param durations 包名到今日使用时长（秒）的映射，缺失的包名按 0 处理
 */
fun appMonitorStatuses(
    apps: List<AppItem>,
    durations: Map<String, Int>
): List<AppMonitorStatus> {
    return apps.map { appItem ->
        AppMonitorStatus(
            appItem = appItem,
            todayDurationSeconds = durations[appItem.packageName] ?: 0
        )
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
 * 状态分组
 *
 * 按完成状态把条目分成两组，供 UI 展示；应用打卡（[AppMonitorStatus]）与
 * 自定义打卡（[CustomCheckStatus]）共用同一分组规则。
 *
 * @param T 分组条目类型
 * @property incomplete 未完成的条目列表
 * @property completed 已完成的条目列表
 */
data class StatusGroup<T>(
    val incomplete: List<T> = emptyList(),
    val completed: List<T> = emptyList()
) {
    /**
     * 是否有未完成的条目
     */
    val hasIncomplete: Boolean
        get() = incomplete.isNotEmpty()

    /**
     * 是否有已完成的条目
     */
    val hasCompleted: Boolean
        get() = completed.isNotEmpty()

    /**
     * 是否为空（没有任何条目）
     */
    val isEmpty: Boolean
        get() = incomplete.isEmpty() && completed.isEmpty()
}