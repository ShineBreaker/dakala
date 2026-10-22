package com.dakala.app.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * 小部件应用项跳转代理
 *
 * 小部件列表项的点击模板必须是 PendingIntent.getActivity：Android 10+ 禁止
 * 广播接收器在后台启动 Activity，经 ACTION_APP_CLICKED 广播转发会被系统
 * 拦截。本 Activity 透明启动，转发到目标应用后立即结束，不呈现界面。
 */
class WidgetLaunchActivity : Activity() {

    companion object {
        private const val TAG = "WidgetLaunchActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(UsageWidgetProvider.EXTRA_PACKAGE_NAME)
            ?.takeIf { it.isNotEmpty() }
            ?.let(::launchTargetApp)
        finish()
    }

    private fun launchTargetApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开应用失败: $packageName", e)
        }
    }
}
