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

        // 获取今日开始时间（0点）
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        // 查询今日的使用统计
        val usageStatsList: List<UsageStats> = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        // 查找目标应用的使用统计
        val targetStats = usageStatsList.find { it.packageName == packageName }
        
        if (targetStats == null) {
            Log.d(TAG, "未找到应用 $packageName 的使用统计")
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

        // 获取今日开始时间（0点）
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        // 查询今日的使用统计
        val usageStatsList: List<UsageStats> = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        // 构建结果映射
        val result = mutableMapOf<String, Int>()
        val statsMap = usageStatsList.associateBy { it.packageName }

        for (packageName in packageNames) {
            val stats = statsMap[packageName]
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

        // 获取今日开始时间（0点）
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        // 查询今日的使用统计
        val usageStatsList: List<UsageStats> = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        // 过滤出有使用记录的应用
        return usageStatsList
            .filter { it.totalTimeInForeground > 0 }
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
            .filter { it.totalTimeInForeground > 0 }
            .associate { it.packageName to (it.totalTimeInForeground / 1000).toInt() }
    }
}