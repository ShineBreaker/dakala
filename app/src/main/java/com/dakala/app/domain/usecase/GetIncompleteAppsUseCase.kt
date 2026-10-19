package com.dakala.app.domain.usecase

import android.content.Context
import com.dakala.app.data.local.dao.AppItemDao
import com.dakala.app.data.local.database.AppUsageDatabase
import com.dakala.app.domain.model.AppMonitorStatus
import com.dakala.app.domain.model.appMonitorStatuses

/**
 * 获取今日未完成打卡的应用用例
 *
 * 未完成打卡的取数只保留这一条路径：先一次性读取受监控应用，再批量查询今日使用时长
 * （逐个应用查询会各发一次全量 UsageStats 扫描），最后按 [AppMonitorStatus.isCompleted] 过滤。
 *
 * 小部件与通知由系统实例化、无法依赖注入，故保留接收 [Context] 的次构造函数。
 *
 * @property appItemDao 应用监控项数据源
 * @property usageStatsUseCase 使用统计查询
 */
class GetIncompleteAppsUseCase(
    private val appItemDao: AppItemDao,
    private val usageStatsUseCase: UsageStatsUseCase
) {

    /**
     * 供无法注入的调用方（小部件、通知）使用
     */
    constructor(context: Context) : this(
        AppUsageDatabase.getInstance(context).appItemDao(),
        UsageStatsUseCase(context)
    )

    /**
     * 获取今日未完成打卡的应用
     *
     * 输出顺序与监控列表一致（按应用名升序）。
     *
     * @return 未完成打卡的应用状态列表
     */
    suspend operator fun invoke(): List<AppMonitorStatus> {
        val monitoredApps = appItemDao.getMonitoredApps()

        // 批量查询今日使用时长，避免每个应用各发一次全量扫描
        val durations = usageStatsUseCase.getAppsUsageDuration(monitoredApps.map { it.packageName })

        return appMonitorStatuses(monitoredApps, durations).filterNot { it.isCompleted }
    }
}
