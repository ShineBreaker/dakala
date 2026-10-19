package com.dakala.app.domain.usecase

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import java.util.Calendar

/**
 * 单个应用的使用统计桶
 *
 * 与 Android 的 UsageStats 解耦，只保留判定「今日有效桶」所需的字段，
 * 使过滤逻辑可以在 JVM 单测中直接覆盖（含跨天边界）。
 *
 * @property packageName 应用包名
 * @property firstTimeStamp 桶开始时间戳（毫秒）
 * @property lastTimeStamp 桶结束时间戳（毫秒）
 * @property totalTimeInForegroundMs 桶内前台使用时长（毫秒）
 */
internal data class UsageBucket(
    val packageName: String,
    val firstTimeStamp: Long,
    val lastTimeStamp: Long,
    val totalTimeInForegroundMs: Long
)

/**
 * 在 [startTime, endTime] 范围内按包名选出最新的使用统计桶，并转换为秒
 *
 * 只保留完全落在查询范围内的桶，排除跨天的周/月/年大桶，
 * 否则会把昨天的使用时长算进今天。
 *
 * @return 使用时长（秒），没有符合条件的桶时返回0
 */
internal fun List<UsageBucket>.latestDurationSeconds(
    packageName: String,
    startTime: Long,
    endTime: Long
): Int {
    val latest = filter { it.packageName == packageName }
        .filter { it.firstTimeStamp >= startTime && it.lastTimeStamp <= endTime }
        .maxByOrNull { it.lastTimeStamp }

    val millis = latest?.totalTimeInForegroundMs ?: return 0
    return (millis / 1000).toInt()
}

/**
 * 应用使用统计用例
 *
 * 封装UsageStatsManager的调用逻辑，提供获取应用使用时长的方法。
 * 这是唯一调用UsageStatsManager的地方，确保权限检查的一致性。
 *
 * 重要说明：
 * - 使用UsageStatsManager需要PACKAGE_USAGE_STATS权限
 * - 该权限需要用户手动在系统设置中开启
 * - 不能通过代码直接请求此权限
 *
 * @property context 应用上下文
 */
class UsageStatsUseCase(private val context: Context) {

    companion object {
        private const val TAG = "UsageStatsUseCase"
    }

    /**
     * 获取今日的开始时间和结束时间戳（本地时区）
     */
    private fun getTodayTimeRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()
        return startTime to endTime
    }

    /**
     * 查询指定时间范围内的使用统计桶
     *
     * 使用 INTERVAL_BEST 获取最细粒度的数据，避免 INTERVAL_DAILY 的时区桶边界问题。
     * 取不到 UsageStatsManager 服务（未授予 PACKAGE_USAGE_STATS）时返回null。
     */
    private fun queryBuckets(startTime: Long, endTime: Long): List<UsageBucket>? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: run {
                Log.w(TAG, "无法获取UsageStatsManager服务")
                return null
            }

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        )
        Log.d(TAG, "queryUsageStats 返回 ${usageStatsList.size} 条记录")

        return usageStatsList.map {
            UsageBucket(
                packageName = it.packageName,
                firstTimeStamp = it.firstTimeStamp,
                lastTimeStamp = it.lastTimeStamp,
                totalTimeInForegroundMs = it.totalTimeInForeground
            )
        }
    }

    /**
     * 获取指定应用今日的使用时长
     *
     * 通过UsageStatsManager查询应用在今日的前台使用时长。
     * 如果没有权限或查询失败，返回0。
     *
     * @param packageName 应用包名
     * @return 使用时长（秒）
     */
    fun getAppUsageDuration(packageName: String): Int {
        val (startTime, endTime) = getTodayTimeRange()
        Log.d(TAG, "查询 $packageName 的使用统计: 范围=${startTime}~${endTime}")

        val durationSeconds = (queryBuckets(startTime, endTime) ?: return 0)
            .latestDurationSeconds(packageName, startTime, endTime)

        Log.d(TAG, "应用 $packageName 今日使用时长: ${durationSeconds}秒")

        return durationSeconds
    }

    /**
     * 获取多个应用今日的使用时长
     *
     * 批量查询多个应用的使用时长，减少重复查询开销。
     *
     * @param packageNames 应用包名列表
     * @return 包名到使用时长（秒）的映射
     */
    fun getAppsUsageDuration(packageNames: List<String>): Map<String, Int> {
        val (startTime, endTime) = getTodayTimeRange()
        Log.d(TAG, "批量查询 ${packageNames.size} 个应用: 范围=${startTime}~${endTime}")

        val buckets = queryBuckets(startTime, endTime) ?: return emptyMap()

        return packageNames.associateWith { packageName ->
            val durationSeconds = buckets.latestDurationSeconds(packageName, startTime, endTime)
            Log.d(TAG, "应用 $packageName 今日使用时长: ${durationSeconds}秒")
            durationSeconds
        }
    }
}
