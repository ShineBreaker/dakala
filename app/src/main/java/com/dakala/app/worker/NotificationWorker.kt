package com.dakala.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.dakala.app.R
import com.dakala.app.data.local.database.AppUsageDatabase
import com.dakala.app.data.local.entity.AppItem
import com.dakala.app.domain.usecase.UsageStatsUseCase
import com.dakala.app.ui.MainActivity
import com.dakala.app.ui.util.PermissionHelper
import com.dakala.app.widget.UsageWidgetProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 通知工作器
 * 
 * 使用WorkManager在用户设置的时间点发送打卡提醒通知。
 * 
 * 功能：
 * 1. 检查今日未打开的应用
 * 2. 检查今日打开时长不足的应用
 * 3. 生成并发送通知
 * 4. 更新桌面小部件
 * 
 * 调度方式：
 * - 使用PeriodicWorkRequest每日定时执行
 * - 使用OneTimeWorkRequest进行初始调度
 */
@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "NotificationWorker"
        private const val CHANNEL_ID = "dakala_reminder_channel"
        private const val NOTIFICATION_ID = 1001
        
        private const val WORK_NAME = "dakala_notification_work"

        /**
         * 调度每日通知任务
         * 
         * @param context 上下文
         * @param hour 小时（0-23）
         * @param minute 分钟（0-59）
         */
        fun scheduleDailyNotification(context: Context, hour: Int = 22, minute: Int = 0) {
            Log.d(TAG, "调度每日通知: $hour:$minute")
            
            // 计算到下次通知时间的延迟
            val now = Calendar.getInstance()
            val targetTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                
                // 如果目标时间已过，设置为明天
                if (before(now)) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            
            val initialDelay = targetTime.timeInMillis - now.timeInMillis
            
            // 创建一次性工作请求（初始延迟后执行）
            val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            
            // 使用唯一工作名称，替换现有任务
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            
            Log.d(TAG, "通知已调度，延迟: ${initialDelay / 1000 / 60} 分钟")
        }

        /**
         * 取消通知任务
         */
        fun cancelNotification(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "通知任务已取消")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "开始执行通知任务")
        
        return try {
            // 检查权限
            if (!PermissionHelper.checkUsageStatsPermission(context)) {
                Log.w(TAG, "无使用统计权限，跳过通知")
                return Result.success()
            }
            
            // 获取未完成的应用
            val incompleteApps = getIncompleteApps()
            
            if (incompleteApps.isEmpty()) {
                Log.d(TAG, "所有应用都已完成打卡")
                // 更新小部件
                UsageWidgetProvider.updateWidget(context)
                return Result.success()
            }
            
            // 发送通知
            sendNotification(incompleteApps)
            
            // 更新小部件
            UsageWidgetProvider.updateWidget(context)
            
            // 调度明天的通知
            scheduleNextNotification()
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "通知任务执行失败", e)
            Result.retry()
        }
    }

    /**
     * 获取未完成打卡的应用
     */
    private suspend fun getIncompleteApps(): List<IncompleteApp> = withContext(Dispatchers.IO) {
        val database = AppUsageDatabase.getInstance(context)
        val appItemDao = database.appItemDao()
        val usageStatsUseCase = UsageStatsUseCase(context)
        
        val monitoredApps = appItemDao.getMonitoredApps()
        
        monitoredApps.mapNotNull { app ->
            val duration = usageStatsUseCase.getAppUsageDuration(app.packageName)
            val isCompleted = duration >= app.durationThreshold
            
            if (!isCompleted) {
                IncompleteApp(
                    app = app,
                    durationSeconds = duration,
                    isOpened = duration > 0
                )
            } else {
                null
            }
        }
    }

    /**
     * 发送通知
     */
    private suspend fun sendNotification(incompleteApps: List<IncompleteApp>) {
        // 创建通知渠道
        createNotificationChannel()
        
        // 检查通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "无通知权限")
                return
            }
        }
        
        // 分组：未打开和时长不足
        val notOpenedApps = incompleteApps.filter { !it.isOpened }
        val insufficientApps = incompleteApps.filter { it.isOpened }
        
        // 构建通知内容
        val contentText = buildNotificationContent(notOpenedApps, insufficientApps)
        
        // 点击打开主应用的Intent
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // 构建通知
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent)
            .build()
        
        // 发送通知
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "通知已发送")
    }

    /**
     * 构建通知内容
     */
    private fun buildNotificationContent(
        notOpenedApps: List<IncompleteApp>,
        insufficientApps: List<IncompleteApp>
    ): String {
        val builder = StringBuilder()
        
        if (notOpenedApps.isNotEmpty()) {
            builder.append(context.getString(R.string.notification_content_not_opened,
                notOpenedApps.joinToString("、") { it.app.appName }))
        }
        
        if (insufficientApps.isNotEmpty()) {
            if (builder.isNotEmpty()) builder.append("\n")
            builder.append(context.getString(R.string.notification_content_insufficient,
                insufficientApps.joinToString("、") { it.app.appName },
                insufficientApps.first().durationSeconds / 60))
        }
        
        return builder.toString()
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 调度下一次通知
     */
    private suspend fun scheduleNextNotification() {
        withContext(Dispatchers.IO) {
            val database = AppUsageDatabase.getInstance(context)
            val settingDao = database.appSettingDao()
            val timeString = settingDao.getSetting("notification_time") ?: "22:00"
            
            val parts = timeString.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 22
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            
            scheduleDailyNotification(context, hour, minute)
        }
    }

    /**
     * 未完成应用数据类
     */
    data class IncompleteApp(
        val app: AppItem,
        val durationSeconds: Int,
        val isOpened: Boolean
    )
}