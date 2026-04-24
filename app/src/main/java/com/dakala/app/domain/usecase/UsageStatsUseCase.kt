package com.dakala.app.domain.usecase

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import java.util.Calendar

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
     * 从UsageStats列表中筛选出真正属于今天本地时间范围的数据，
     * 并按 lastTimeStamp 降序排序，确保取到最新的记录。
     */
    private fun filterTodayStats(
        usageStatsList: List<UsageStats>,
        todayStart: Long,
        todayEnd: Long,
        packageName: String
    ): UsageStats? {
        val filtered = usageStatsList
            .filter { it.packageName == packageName }
            .filter {
                // 排除跨日大桶（周/月/年），只保留今天开始的桶
                it.firstTimeStamp >= todayStart && it.lastTimeStamp <= todayEnd
            }
            .sortedByDescending { it.lastTimeStamp }

        if (filtered.isEmpty()) {
            Log.d(TAG, "未找到应用 $packageName 在今日 (${todayStart} ~ ${todayEnd}) 的使用统计")
            return null
        }

        // 打印所有匹配到的桶，用于调试时区/边界问题
        filtered.forEachIndexed { index, stats ->
            Log.d(
                TAG,
                "  [${index}] ${stats.packageName}: " +
                    "bucket=${stats.firstTimeStamp}~${stats.lastTimeStamp}, " +
                    "totalTime=${stats.totalTimeInForeground}ms"
            )
        }

        return filtered.first()
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
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: run {
                Log.w(TAG, "无法获取UsageStatsManager服务")
                return 0
            }

        val (startTime, endTime) = getTodayTimeRange()
        Log.d(TAG, "查询 $packageName 的使用统计: 范围=${startTime}~${endTime}")

        // 使用 INTERVAL_BEST 获取最细粒度的数据，避免 INTERVAL_DAILY 的时区桶边界问题
        val usageStatsList: List<UsageStats> = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        )

        Log.d(TAG, "queryUsageStats 返回 ${usageStatsList.size} 条记录")

        val targetStats = filterTodayStats(usageStatsList, startTime, endTime, packageName)

        if (targetStats == null) {
            Log.d(TAG, "未找到应用 $packageName 今日有效使用统计")
            return 0
        }

        // 将毫秒转换为秒
        val durationSeconds = (targetStats.totalTimeInForeground / 1000).toInt()
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
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: run {
                Log.w(TAG, "无法获取UsageStatsManager服务")
                return emptyMap()
            }

        val (startTime, endTime) = getTodayTimeRange()
        Log.d(TAG, "批量查询 ${packageNames.size} 个应用: 范围=${startTime}~${endTime}")

        // 使用 INTERVAL_BEST 获取最细粒度的数据
        val usageStatsList: List<UsageStats> = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        )

        Log.d(TAG, "queryUsageStats 返回 ${usageStatsList.size} 条记录")

        // 对每个应用，找到最新的且与今天有重叠的统计桶
        val result = mutableMapOf<String, Int>()
        for (packageName in packageNames) {
            val stats = filterTodayStats(usageStatsList, startTime, endTime, packageName)
            val durationSeconds = if (stats != null) {
                (stats.totalTimeInForeground / 1000).toInt()
            } else {
                0
            }
            result[packageName] = durationSeconds
            Log.d(TAG, "应用 $packageName 今日使用时长: ${durationSeconds}秒")
        }

        return result
    }

    /**
     * 检查应用今日是否被打开过
     * 
     * @param packageName 应用包名
     * @return 是否被打开过
     */
    fun isAppOpenedToday(packageName: String): Boolean {
        return getAppUsageDuration(packageName) > 0
    }

    /**
     * 获取今日所有有使用记录的应用
     *
     * @return 包名到使用时长（秒）的映射
     */
    fun getAllUsedAppsToday(): Map<String, Int> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: run {
                Log.w(TAG, "无法获取UsageStatsManager服务")
                return emptyMap()
            }

        val (startTime, endTime) = getTodayTimeRange()

        // 使用 INTERVAL_BEST 获取最细粒度的数据
        val usageStatsList: List<UsageStats> = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        )

        // 只保留今天开始的桶，排除跨日大桶
        return usageStatsList
            .filter {
                it.totalTimeInForeground > 0 &&
                    it.firstTimeStamp >= startTime && it.lastTimeStamp <= endTime
            }
            .sortedByDescending { it.lastTimeStamp }
            .distinctBy { it.packageName }
            .associate { it.packageName to (it.totalTimeInForeground / 1000).toInt() }
    }

    /**
     * 获取指定日期范围的应用使用时长
     * 
     * @param startTime 开始时间戳（毫秒）
     * @param endTime 结束时间戳（毫秒）
     * @return 包名到使用时长（秒）的映射
     */
    fun getAppsUsageDurationInRange(startTime: Long, endTime: Long): Map<String, Int> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: run {
                Log.w(TAG, "无法获取UsageStatsManager服务")
                return emptyMap()
            }

        val usageStatsList: List<UsageStats> = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        )

        return usageStatsList
            .filter {
                it.totalTimeInForeground > 0 &&
                    it.firstTimeStamp >= startTime && it.lastTimeStamp <= endTime
            }
            .sortedByDescending { it.lastTimeStamp }
            .distinctBy { it.packageName }
            .associate { it.packageName to (it.totalTimeInForeground / 1000).toInt() }
    }
}