package com.dakala.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.RemoteViews
import com.dakala.app.R
import com.dakala.app.data.local.database.AppUsageDatabase
import com.dakala.app.data.local.entity.AppItem
import com.dakala.app.domain.usecase.UsageStatsUseCase
import com.dakala.app.ui.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 应用使用状态桌面小部件
 *
 * 显示未完成打卡的应用列表，点击可快速打开对应应用。
 *
 * 功能特点：
 * 1. 显示未打开和时长不足的应用
 * 2. 点击应用项可直接打开应用
 * 3. 定期自动刷新（通过WorkManager，每15分钟）
 * 4. 支持手动刷新按钮
 * 5. 屏幕解锁时自动刷新
 *
 * 更新机制：
 * - 系统定期调用onUpdate刷新小部件
 * - WorkManager定期刷新（更可靠）
 * - 屏幕解锁时刷新
 * - 手动点击刷新按钮
 */
class UsageWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "UsageWidgetProvider"

        const val ACTION_APP_CLICKED = "com.dakala.app.ACTION_APP_CLICKED"
        const val ACTION_REFRESH = "com.dakala.app.ACTION_REFRESH_WIDGET"
        const val EXTRA_PACKAGE_NAME = "package_name"

        /**
         * 手动更新所有小部件
         *
         * @param context 上下文
         */
        fun updateWidget(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, UsageWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            val intent = Intent(context, UsageWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate: 更新 ${appWidgetIds.size} 个小部件")

        // 使用协程异步加载数据
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateAllWidgets(context, appWidgetManager, appWidgetIds)
            } catch (e: Exception) {
                Log.e(TAG, "更新小部件失败", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_APP_CLICKED -> {
                // 处理应用点击事件
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                if (!packageName.isNullOrEmpty()) {
                    openApp(context, packageName)
                }
            }
            ACTION_REFRESH -> {
                // 处理手动刷新事件
                Log.d(TAG, "onReceive: 手动刷新小部件")
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, UsageWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        updateAllWidgets(context, appWidgetManager, appWidgetIds)
                    } catch (e: Exception) {
                        Log.e(TAG, "手动刷新小部件失败", e)
                    }
                }
            }
        }
    }

    /**
     * 更新所有小部件
     */
    private suspend fun updateAllWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 检查权限
        if (!PermissionHelper.checkUsageStatsPermission(context)) {
            // 无权限时显示提示
            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_usage)
                views.setTextViewText(R.id.widget_title, "需要权限")
                views.setTextViewText(R.id.widget_empty, "请打开应用授权")
                views.setViewVisibility(R.id.widget_list, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
            return
        }

        // 获取未完成的应用列表
        val incompleteApps = getIncompleteApps(context)

        // 更新每个小部件
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, incompleteApps)
        }
    }

    /**
     * 获取未完成打卡的应用列表
     */
    private suspend fun getIncompleteApps(context: Context): List<AppItemWithStatus> {
        val database = AppUsageDatabase.getInstance(context)
        val appItemDao = database.appItemDao()
        val usageStatsUseCase = UsageStatsUseCase(context)

        // 获取所有被监控的应用
        val monitoredApps = appItemDao.getMonitoredApps()

        // 筛选未完成的应用
        return monitoredApps.mapNotNull { app ->
            val duration = usageStatsUseCase.getAppUsageDuration(app.packageName)
            val isCompleted = duration >= app.durationThreshold

            if (!isCompleted) {
                AppItemWithStatus(
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
     * 更新单个小部件
     */
    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        incompleteApps: List<AppItemWithStatus>
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_usage)

        // 设置点击标题跳转到本应用
        val mainIntent = Intent(context, com.dakala.app.ui.MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_title, mainPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_empty, mainPendingIntent)

        // 设置刷新按钮点击事件
        val refreshIntent = Intent(context, UsageWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)

        if (incompleteApps.isEmpty()) {
            // 所有应用都已完成
            views.setViewVisibility(R.id.widget_list, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_all_completed))
        } else {
            // 显示未完成的应用列表
            views.setViewVisibility(R.id.widget_list, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)

            // 设置列表项点击模板 - 用于配合 setOnClickFillInIntent
            val appClickIntent = Intent(context, UsageWidgetProvider::class.java).apply {
                action = ACTION_APP_CLICKED
            }
            val appClickPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                appClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, appClickPendingIntent)

            // 设置列表适配器 - WidgetService 会直接查询数据库
            val intent = Intent(context, WidgetService::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            views.setRemoteAdapter(R.id.widget_list, intent)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    /**
     * 打开指定应用
     */
    private fun openApp(context: Context, packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开应用失败: $packageName", e)
        }
    }

    /**
     * 应用状态数据类
     */
    data class AppItemWithStatus(
        val app: AppItem,
        val durationSeconds: Int,
        val isOpened: Boolean
    ) {
        fun toParcelable(): AppParcelable {
            return AppParcelable(
                packageName = app.packageName,
                appName = app.appName,
                durationSeconds = durationSeconds,
                isOpened = isOpened
            )
        }
    }

    /**
     * 可序列化的应用数据（用于 Intent 传递）
     */
    data class AppParcelable(
        val packageName: String,
        val appName: String,
        val durationSeconds: Int,
        val isOpened: Boolean
    ) : android.os.Parcelable {
        constructor(parcel: android.os.Parcel) : this(
            parcel.readString() ?: "",
            parcel.readString() ?: "",
            parcel.readInt(),
            parcel.readByte() != 0.toByte()
        )

        override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {
            parcel.writeString(packageName)
            parcel.writeString(appName)
            parcel.writeInt(durationSeconds)
            parcel.writeByte(if (isOpened) 1 else 0)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : android.os.Parcelable.Creator<AppParcelable> {
            override fun createFromParcel(parcel: android.os.Parcel): AppParcelable {
                return AppParcelable(parcel)
            }

            override fun newArray(size: Int): Array<AppParcelable?> {
                return arrayOfNulls(size)
            }
        }
    }
}
