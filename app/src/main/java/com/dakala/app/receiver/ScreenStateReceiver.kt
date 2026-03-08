package com.dakala.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dakala.app.widget.UsageWidgetProvider

/**
 * 屏幕状态接收器
 *
 * 在屏幕解锁时刷新桌面小部件，确保用户看到最新的打卡状态。
 */
class ScreenStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenStateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_USER_PRESENT -> {
                // 用户解锁屏幕
                Log.d(TAG, "用户解锁屏幕，刷新小部件")
                UsageWidgetProvider.updateWidget(context)
            }
        }
    }
}