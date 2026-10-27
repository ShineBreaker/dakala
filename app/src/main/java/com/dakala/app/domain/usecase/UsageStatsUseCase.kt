package com.dakala.app.domain.usecase

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.dakala.app.domain.model.DayResetTime
import java.time.Clock

/**
 * 单个应用的使用统计查询项
 *
 * @property packageName 应用包名
 * @property resetMinutesOfDay 该应用打卡日的重置点（距零点分钟数，见 [DayResetTime]）
 */
data class AppUsageQuery(
    val packageName: String,
    val resetMinutesOfDay: Int
)

/**
 * 前台事件类别（与 Android 的 UsageEvents 解耦，供 JVM 单测覆盖）
 */
internal enum class ForegroundEventKind { RESUMED, PAUSED }

/**
 * 单条前台事件
 *
 * @property packageName 应用包名
 * @property className 事件关联的 Activity 类名（可能为 null）
 * @property timeStamp 事件时间戳（毫秒）
 * @property kind 事件类别
 */
internal data class ForegroundEvent(
    val packageName: String,
    val className: String?,
    val timeStamp: Long,
    val kind: ForegroundEventKind
)

/**
 * 由前台事件流计算 [packageName] 在 [startTime, endTime] 内的前台总时长
 *
 * 事件先按时间戳排序，再用「按类名计数」判定应用是否处于前台：
 * 任意 Activity 处于 RESUMED 即前台，全部 PAUSED 即后台——
 * 因此可容忍 Activity 切换时 RESUME 先于 PAUSE 到达的乱序。
 *
 * 事件流应覆盖窗口起点之前的一段回溯：窗口起点前的事件只参与
 * 前台状态回放、不直接计时，用于识别「起点时已在前台」的会话
 * （如重置点之前打开、至今未退出的应用）。
 *
 * @return 前台使用时长（秒）
 */
internal fun List<ForegroundEvent>.foregroundSeconds(
    packageName: String,
    startTime: Long,
    endTime: Long
): Int {
    var totalMillis = 0L
    var foregroundSince = -1L
    var resumedCount = 0
    val resumedClasses = HashMap<String, Int>()

    for (event in this.sortedBy { it.timeStamp }) {
        if (event.packageName != packageName) continue
        if (event.timeStamp > endTime) break

        val key = event.className ?: event.packageName
        when (event.kind) {
            ForegroundEventKind.RESUMED -> {
                resumedClasses[key] = (resumedClasses[key] ?: 0) + 1
                resumedCount++
                if (resumedCount == 1) foregroundSince = event.timeStamp
            }
            ForegroundEventKind.PAUSED -> {
                val count = resumedClasses[key] ?: 0
                if (count > 0) {
                    resumedClasses[key] = count - 1
                    resumedCount--
                    if (resumedCount == 0 && foregroundSince >= 0) {
                        val from = maxOf(foregroundSince, startTime)
                        if (event.timeStamp > from) {
                            totalMillis += event.timeStamp - from
                        }
                        foregroundSince = -1
                    }
                }
            }
        }
    }

    if (foregroundSince >= 0) {
        val from = maxOf(foregroundSince, startTime)
        if (endTime > from) {
            totalMillis += endTime - from
        }
    }

    return (totalMillis / 1000).toInt().coerceAtLeast(0)
}

/**
 * 应用使用统计用例
 *
 * 封装UsageStatsManager的调用逻辑，提供获取应用使用时长的方法。
 * 这是唯一调用UsageStatsManager的地方，确保权限检查的一致性。
 *
 * 打卡日界由每个应用自己的重置时间决定（默认 00:00，可为单个应用
 * 设置如 23:00、04:00）。查询走 queryEvents 事件流而非日聚合桶：
 * 日桶按自然日对齐，无法表达「23 点翻篇」这类自定义边界。
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

        /**
         * 事件查询向前回溯量：窗口起点前已在前台的会话靠它回放出来
         */
        private const val EVENT_LOOKBACK_MS = 24 * 60 * 60 * 1000L
    }

    /**
     * 查询指定时间范围内的前台事件流
     *
     * 取不到 UsageStatsManager 服务（未授予 PACKAGE_USAGE_STATS）时返回null。
     */
    private fun queryForegroundEvents(startTime: Long, endTime: Long): List<ForegroundEvent>? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: run {
                Log.w(TAG, "无法获取UsageStatsManager服务")
                return null
            }

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val events = ArrayList<ForegroundEvent>()
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val kind = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundEventKind.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> ForegroundEventKind.PAUSED
                else -> continue
            }
            events += ForegroundEvent(
                packageName = event.packageName,
                className = event.className,
                timeStamp = event.timeStamp,
                kind = kind
            )
        }
        Log.d(TAG, "queryEvents 返回 ${events.size} 条前台事件")
        return events
    }

    /**
     * 获取多个应用在当前打卡周期内的使用时长
     *
     * 每个应用按自己的重置时间取「最近一次重置点到现在」的时长；
     * 事件流只向系统查询一次（覆盖最早周期起点到现在），各应用自行裁剪。
     *
     * @param queries 查询项列表（包名 + 各自重置点）
     * @return 包名到使用时长（秒）的映射
     */
    fun getAppsUsageDuration(queries: List<AppUsageQuery>): Map<String, Int> {
        if (queries.isEmpty()) return emptyMap()

        val clock = Clock.systemDefaultZone()
        val now = clock.millis()
        val periodStarts = queries.associate { query ->
            query.packageName to DayResetTime.ofMinutes(query.resetMinutesOfDay).periodStartMillis(clock)
        }
        val rangeStart = periodStarts.values.min() - EVENT_LOOKBACK_MS
        Log.d(TAG, "批量查询 ${queries.size} 个应用: 范围=${rangeStart}~${now}")

        val events = queryForegroundEvents(rangeStart, now) ?: return emptyMap()

        return queries.associate { query ->
            val durationSeconds = events.foregroundSeconds(
                packageName = query.packageName,
                startTime = periodStarts.getValue(query.packageName),
                endTime = now
            )
            Log.d(TAG, "应用 ${query.packageName} 本周期使用时长: ${durationSeconds}秒")
            query.packageName to durationSeconds
        }
    }
}
