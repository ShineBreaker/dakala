package com.dakala.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dakala.app.widget.UsageWidgetProvider
import com.dakala.app.worker.NotificationWorker
import com.dakala.app.worker.WidgetRefreshWorker

/**
 * 开机启动接收器
 *
 * 在设备启动完成后重新调度后台任务。
 * 因为WorkManager的任务在设备重启后会被清除，需要重新调度。
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "设备启动完成，重新调度任务")

            // 重新调度每日通知
            NotificationWorker.scheduleDailyNotification(context)

            // 调度小部件定期刷新
            WidgetRefreshWorker.schedulePeriodicRefresh(context)

            // 立即刷新一次小部件
            UsageWidgetProvider.updateWidget(context)
        }
    }
}