package com.dakala.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.dakala.app.ui.util.PermissionHelper
import com.dakala.app.widget.UsageWidgetProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 小部件刷新工作器
 *
 * 使用WorkManager定期刷新桌面小部件。
 * 相比系统自带的updatePeriodMillis，WorkManager更可靠。
 *
 * 刷新策略：
 * - 每15分钟刷新一次（WorkManager最小间隔）
 * - 仅在有使用统计权限时才刷新
 */
@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "WidgetRefreshWorker"
        private const val WORK_NAME = "dakala_widget_refresh_work"
        private const val REFRESH_INTERVAL_MINUTES = 15L // WorkManager最小间隔为15分钟

        /**
         * 调度定期刷新任务
         */
        fun schedulePeriodicRefresh(context: Context) {
            Log.d(TAG, "调度小部件定期刷新任务")

            val workRequest = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                REFRESH_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false) // 不限制电量，确保及时刷新
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    5,
                    TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Log.d(TAG, "小部件定期刷新任务已调度，间隔: $REFRESH_INTERVAL_MINUTES 分钟")
        }

        /**
         * 取消定期刷新任务
         */
        fun cancelPeriodicRefresh(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "小部件定期刷新任务已取消")
        }

        /**
         * 立即刷新小部件（一次性任务）
         */
        fun refreshNow(context: Context) {
            Log.d(TAG, "立即刷新小部件")

            val workRequest = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setConstraints(
                    Constraints.Builder().build()
                )
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "开始刷新小部件")

        return try {
            // 检查权限
            if (!PermissionHelper.checkUsageStatsPermission(context)) {
                Log.w(TAG, "无使用统计权限，跳过刷新")
                return Result.success()
            }

            // 刷新小部件
            UsageWidgetProvider.updateWidget(context)

            Log.d(TAG, "小部件刷新完成")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "小部件刷新失败", e)
            Result.retry()
        }
    }
}