package com.dakala.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.dakala.app.R

/**
 * 小部件列表服务
 * 
 * 为小部件的ListView提供数据。
 * 使用RemoteViewsService实现，支持在桌面小部件中显示列表。
 */
class WidgetService : RemoteViewsService() {
    
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WidgetRemoteViewsFactory(applicationContext, intent)
    }
}

/**
 * 小部件列表视图工厂
 * 
 * 负责创建和管理小部件列表中的每一项视图。
 */
class WidgetRemoteViewsFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    companion object {
        private const val TAG = "WidgetRemoteViewsFactory"
    }

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var apps: List<UsageWidgetProvider.AppParcelable> = emptyList()

    override fun onCreate() {
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        Log.d(TAG, "onCreate: appWidgetId=$appWidgetId")
    }

    override fun onDataSetChanged() {
        // 从Intent中获取应用列表
        @Suppress("DEPRECATION")
        apps = intent.getParcelableArrayListExtra("apps") ?: emptyList()
        Log.d(TAG, "onDataSetChanged: ${apps.size} apps")
    }

    override fun onDestroy() {
        apps = emptyList()
    }

    override fun getCount(): Int = apps.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position < 0 || position >= apps.size) {
            return RemoteViews(context.packageName, R.layout.widget_item)
        }

        val app = apps[position]
        val views = RemoteViews(context.packageName, R.layout.widget_item)

        // 设置应用名称
        views.setTextViewText(R.id.app_name, app.appName)

        // 设置状态文本
        val statusText = if (app.isOpened) {
            val minutes = app.durationSeconds / 60
            val seconds = app.durationSeconds % 60
            if (minutes > 0) {
                "${minutes}分钟${seconds}秒"
            } else {
                "${seconds}秒"
            }
        } else {
            context.getString(R.string.status_not_opened)
        }
        views.setTextViewText(R.id.app_status, statusText)

        // 设置应用图标
        try {
            val icon = context.packageManager.getApplicationIcon(app.packageName)
            val bitmap = drawableToBitmap(icon)
            views.setImageViewBitmap(R.id.app_icon, bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "获取应用图标失败: ${app.packageName}", e)
        }

        // 设置点击事件
        val fillInIntent = Intent().apply {
            action = UsageWidgetProvider.ACTION_APP_CLICKED
            putExtra(UsageWidgetProvider.EXTRA_PACKAGE_NAME, app.packageName)
        }
        views.setOnClickFillInIntent(R.id.app_icon, fillInIntent)
        views.setOnClickFillInIntent(R.id.app_name, fillInIntent)
        views.setOnClickFillInIntent(R.id.app_status, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false

    /**
     * 将Drawable转换为Bitmap
     */
    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap {
        if (drawable is android.graphics.drawable.BitmapDrawable) {
            return drawable.bitmap
        }

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }
}

// 需要导入的Bitmap和Canvas
import android.graphics.Bitmap
import android.graphics.Canvas