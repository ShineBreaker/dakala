package com.dakala.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.dakala.app.R
import com.dakala.app.data.local.database.AppUsageDatabase
import com.dakala.app.domain.usecase.UsageStatsUseCase
import com.dakala.app.ui.util.PermissionHelper
import kotlinx.coroutines.runBlocking
import java.io.InputStream

/**
 * 小部件列表服务
 */
class WidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val tabType = intent.getIntExtra("tab_type", 0)
        return if (tabType == 1) {
            CustomCheckRemoteViewsFactory(applicationContext, intent)
        } else {
            WidgetRemoteViewsFactory(applicationContext, intent)
        }
    }
}

/**
 * 小部件列表视图工厂 - 应用打卡
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
        Log.d(TAG, "onDataSetChanged: 开始查询数据")
        apps = runBlocking {
            getIncompleteApps()
        }
        Log.d(TAG, "onDataSetChanged: 查询到 ${apps.size} 个应用")
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

        views.setTextViewText(R.id.app_name, app.appName)

        val statusText = if (app.isOpened) {
            val minutes = app.durationSeconds / 60
            val seconds = app.durationSeconds % 60
            if (minutes > 0) "${minutes}分钟${seconds}秒" else "${seconds}秒"
        } else {
            context.getString(R.string.status_not_opened)
        }
        views.setTextViewText(R.id.app_status, statusText)

        try {
            val icon = context.packageManager.getApplicationIcon(app.packageName)
            val bitmap = drawableToBitmap(icon)
            views.setImageViewBitmap(R.id.app_icon, bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "获取应用图标失败: ${app.packageName}", e)
        }

        val fillInIntent = Intent().apply {
            putExtra(UsageWidgetProvider.EXTRA_PACKAGE_NAME, app.packageName)
        }
        views.setOnClickFillInIntent(R.id.widget_item_root, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false

    private suspend fun getIncompleteApps(): List<UsageWidgetProvider.AppParcelable> {
        if (!PermissionHelper.checkUsageStatsPermission(context)) {
            Log.w(TAG, "无使用统计权限")
            return emptyList()
        }

        val database = AppUsageDatabase.getInstance(context)
        val appItemDao = database.appItemDao()
        val usageStatsUseCase = UsageStatsUseCase(context)

        val monitoredApps = appItemDao.getMonitoredApps()
        Log.d(TAG, "获取到 ${monitoredApps.size} 个监控应用")

        return monitoredApps.mapNotNull { app ->
            val duration = usageStatsUseCase.getAppUsageDuration(app.packageName)
            val isCompleted = duration >= app.durationThreshold

            if (!isCompleted) {
                UsageWidgetProvider.AppParcelable(
                    packageName = app.packageName,
                    appName = app.appName,
                    durationSeconds = duration,
                    isOpened = duration > 0
                )
            } else {
                null
            }
        }
    }

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

/**
 * 小部件列表视图工厂 - 自定义打卡
 */
class CustomCheckRemoteViewsFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    companion object {
        private const val TAG = "CustomCheckRemoteViewsFactory"
    }

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var items: List<UsageWidgetProvider.CustomCheckItemWithStatus> = emptyList()

    override fun onCreate() {
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        Log.d(TAG, "onCreate: appWidgetId=$appWidgetId")
    }

    override fun onDataSetChanged() {
        Log.d(TAG, "onDataSetChanged: 开始查询数据")
        items = runBlocking {
            getCustomCheckItems()
        }
        Log.d(TAG, "onDataSetChanged: 查询到 ${items.size} 个自定义打卡项")
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position < 0 || position >= items.size) {
            return RemoteViews(context.packageName, R.layout.widget_custom_check_item)
        }

        val item = items[position]
        val views = RemoteViews(context.packageName, R.layout.widget_custom_check_item)

        // 设置图标
        if (item.item.iconType == "image") {
            // 图片类型 - 显示 ImageView，隐藏 TextView
            views.setViewVisibility(R.id.item_icon, android.view.View.GONE)
            views.setViewVisibility(R.id.item_icon_image, android.view.View.VISIBLE)
            
            try {
                val uri = Uri.parse(item.item.iconData)
                val bitmap = loadRoundedBitmap(uri)
                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.item_icon_image, bitmap)
                } else {
                    // 加载失败，显示首字母
                    views.setViewVisibility(R.id.item_icon, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.item_icon_image, android.view.View.GONE)
                    views.setTextViewText(R.id.item_icon, item.item.name.firstOrNull()?.toString() ?: "?")
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载图片失败", e)
                views.setViewVisibility(R.id.item_icon, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.item_icon_image, android.view.View.GONE)
                views.setTextViewText(R.id.item_icon, item.item.name.firstOrNull()?.toString() ?: "?")
            }
        } else {
            // Emoji 类型 - 显示 TextView，隐藏 ImageView
            views.setViewVisibility(R.id.item_icon, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.item_icon_image, android.view.View.GONE)
            views.setTextViewText(R.id.item_icon, item.item.iconData)
        }

        // 设置名称
        views.setTextViewText(R.id.item_name, item.item.name)

        // 设置打钩状态 - 未完成显示空心圆，已完成显示实心勾
        views.setImageViewResource(
            R.id.item_check,
            if (item.isCompleted) R.drawable.ic_check_filled else R.drawable.ic_check_outline
        )

        // 设置点击事件
        val fillInIntent = Intent().apply {
            putExtra(UsageWidgetProvider.EXTRA_ITEM_ID, item.item.id)
            putExtra(UsageWidgetProvider.EXTRA_IS_COMPLETED, item.isCompleted)
        }
        views.setOnClickFillInIntent(R.id.widget_item_root, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false

    private suspend fun getCustomCheckItems(): List<UsageWidgetProvider.CustomCheckItemWithStatus> {
        return try {
            val database = AppUsageDatabase.getInstance(context)
            val customCheckItemDao = database.customCheckItemDao()
            val customCheckRecordDao = database.customCheckRecordDao()

            val allItems = customCheckItemDao.getAllItems()
            Log.d(TAG, "获取到 ${allItems.size} 个自定义打卡项")

            val today = getTodayDate()
            val records = customCheckRecordDao.getRecordsByDate(today)
            val completedItemIds = records.filter { it.isCompleted }.map { it.itemId }.toSet()

            allItems.map { item ->
                UsageWidgetProvider.CustomCheckItemWithStatus(
                    item = item,
                    isCompleted = item.id in completedItemIds
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取自定义打卡项失败", e)
            emptyList()
        }
    }

    private fun getTodayDate(): Int {
        val calendar = java.util.Calendar.getInstance()
        return calendar.get(java.util.Calendar.YEAR) * 10000 +
                (calendar.get(java.util.Calendar.MONTH) + 1) * 100 +
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
    }

    /**
     * 加载图片并裁剪为圆角矩形
     */
    private fun loadRoundedBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = inputStream?.use { BitmapFactory.decodeStream(it) } ?: return null

            // 创建圆角位图
            val size = minOf(bitmap.width, bitmap.height)
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)

            val paint = Paint()
            paint.isAntiAlias = true
            paint.color = android.graphics.Color.WHITE

            val rectF = RectF(0f, 0f, size.toFloat(), size.toFloat())
            val radius = size * 0.2f // 20% 圆角
            canvas.drawRoundRect(rectF, radius, radius, paint)

            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, null, rectF, paint)

            output
        } catch (e: Exception) {
            Log.e(TAG, "加载圆角图片失败", e)
            null
        }
    }
}