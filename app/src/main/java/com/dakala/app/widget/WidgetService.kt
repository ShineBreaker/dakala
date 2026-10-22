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
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.dakala.app.R
import com.dakala.app.data.local.database.AppUsageDatabase
import com.dakala.app.data.local.entity.AppSetting
import com.dakala.app.data.org.OrgNote
import com.dakala.app.data.org.sortedForDisplay
import com.dakala.app.data.repository.OrgTodoRepository
import com.dakala.app.domain.model.AppMonitorStatus
import com.dakala.app.domain.model.DayStamp
import com.dakala.app.domain.usecase.GetIncompleteAppsUseCase
import com.dakala.app.ui.util.PermissionHelper
import kotlinx.coroutines.runBlocking
import java.io.InputStream

/**
 * 小部件列表服务
 */
class WidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return when (intent.getIntExtra("tab_type", 0)) {
            1 -> CustomCheckRemoteViewsFactory(applicationContext, intent)
            2 -> TodoRemoteViewsFactory(applicationContext, intent)
            else -> WidgetRemoteViewsFactory(applicationContext, intent)
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
    private var apps: List<AppMonitorStatus> = emptyList()

    // 调色板随每次数据刷新重建：factory 实例跨明暗切换存活，
    // lazy 缓存会把深色色值带进浅色模式渲染
    private var palette: WidgetPalette = WidgetPalette.current(context)

    override fun onCreate() {
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        Log.d(TAG, "onCreate: appWidgetId=$appWidgetId")
    }

    override fun onDataSetChanged() {
        Log.d(TAG, "onDataSetChanged: 开始查询数据")
        palette = WidgetPalette.current(context)
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

        views.setTextViewText(R.id.app_name, app.appItem.appName)
        views.setTextColor(R.id.app_name, palette.onSurface)

        val statusText = if (app.isOpenedToday) {
            app.todayDurationFormatted
        } else {
            context.getString(R.string.status_not_opened)
        }
        views.setTextViewText(R.id.app_status, statusText)
        views.setTextColor(R.id.app_status, palette.secondary)

        try {
            val icon = context.packageManager.getApplicationIcon(app.appItem.packageName)
            val bitmap = drawableToBitmap(icon)
            views.setImageViewBitmap(R.id.app_icon, bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "获取应用图标失败: ${app.appItem.packageName}", e)
        }

        val fillInIntent = Intent().apply {
            putExtra(UsageWidgetProvider.EXTRA_PACKAGE_NAME, app.appItem.packageName)
        }
        views.setOnClickFillInIntent(R.id.widget_item_root, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false

    private suspend fun getIncompleteApps(): List<AppMonitorStatus> {
        if (!PermissionHelper.checkUsageStatsPermission(context)) {
            Log.w(TAG, "无使用统计权限")
            return emptyList()
        }

        return GetIncompleteAppsUseCase(context)()
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

        // 图标目标尺寸上限（px）：解码采样基准，24dp 容器在最高密度下也只需 96px
        private const val ICON_TARGET_SIZE = 144
    }

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var items: List<UsageWidgetProvider.CustomCheckItemWithStatus> = emptyList()

    // 调色板随每次数据刷新重建，避免跨明暗切换后用旧色值渲染
    private var palette: WidgetPalette = WidgetPalette.current(context)

    // 圆角图标缓存：宿主滚动列表会反复回调 getViewAt，避免每行重复磁盘解码
    private val iconCache = mutableMapOf<String, Bitmap>()

    override fun onCreate() {
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        Log.d(TAG, "onCreate: appWidgetId=$appWidgetId")
    }

    override fun onDataSetChanged() {
        Log.d(TAG, "onDataSetChanged: 开始查询数据")
        palette = WidgetPalette.current(context)
        items = runBlocking {
            getCustomCheckItems()
        }
        Log.d(TAG, "onDataSetChanged: 查询到 ${items.size} 个自定义打卡项")
    }

    override fun onDestroy() {
        items = emptyList()
        iconCache.clear()
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
                val iconData = item.item.iconData
                val bitmap = iconCache[iconData]
                    ?: loadRoundedBitmap(Uri.parse(iconData))?.also { iconCache[iconData] = it }
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
        views.setTextColor(R.id.item_name, palette.onSurface)

        // 设置打钩状态 - 未完成显示空心圆，已完成显示实心勾
        views.setImageViewResource(
            R.id.item_check,
            if (item.isCompleted) R.drawable.ic_check_filled else R.drawable.ic_check_outline
        )
        views.setInt(
            R.id.item_check,
            "setColorFilter",
            if (item.isCompleted) palette.success else palette.secondary
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

            val today = DayStamp.today()
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

    /**
     * 加载图片并裁剪为圆角矩形
     */
    private fun loadRoundedBitmap(uri: Uri): Bitmap? {
        return try {
            // 先只读原图尺寸：按图标目标大小采样后再解码，
            // 避免相机原图整张解码进内存并经 Binder 回传桌面进程
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
            }
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = inputStream?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

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

    /** 计算 2 的幂采样率，使解码结果仍不小于图标目标尺寸 */
    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var inSampleSize = 1
        if (width > ICON_TARGET_SIZE || height > ICON_TARGET_SIZE) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            while (halfWidth / inSampleSize >= ICON_TARGET_SIZE && halfHeight / inSampleSize >= ICON_TARGET_SIZE) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}

/**
 * 小部件列表视图工厂 - Org 待办
 */
class TodoRemoteViewsFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    companion object {
        private const val TAG = "TodoRemoteViewsFactory"
    }

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var notes: List<OrgNote> = emptyList()

    // 调色板随每次数据刷新重建，避免跨明暗切换后用旧色值渲染
    private var palette: WidgetPalette = WidgetPalette.current(context)

    override fun onCreate() {
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        Log.d(TAG, "onCreate: appWidgetId=$appWidgetId")
    }

    override fun onDataSetChanged() {
        Log.d(TAG, "onDataSetChanged: 开始读取 org 待办")
        palette = WidgetPalette.current(context)
        notes = runBlocking { loadTodoNotes() }
        Log.d(TAG, "onDataSetChanged: 读取到 ${notes.size} 个待办")
    }

    /**
     * 按用户设置过滤：缺省仅未完成；显示全部时未完成排前，
     * 已完成按 CLOSED 完成时间从晚到早
     */
    private suspend fun filterNotes(all: List<OrgNote>): List<OrgNote> {
        val showAll = AppUsageDatabase.getInstance(context).appSettingDao()
            .getSetting(AppSetting.KEY_WIDGET_TODO_SHOW_ALL).toBoolean()
        return if (showAll) all.sortedForDisplay() else all.filter { !it.isDone }
    }

    override fun onDestroy() {
        notes = emptyList()
    }

    override fun getCount(): Int = notes.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position < 0 || position >= notes.size) {
            return RemoteViews(context.packageName, R.layout.widget_todo_item)
        }

        val note = notes[position]
        val views = RemoteViews(context.packageName, R.layout.widget_todo_item)

        // 完成状态图标
        views.setImageViewResource(
            R.id.item_check,
            if (note.isDone) R.drawable.ic_check_filled else R.drawable.ic_check_outline
        )
        views.setInt(
            R.id.item_check,
            "setColorFilter",
            if (note.isDone) palette.success else palette.secondary
        )

        // 状态关键字（未完成的用错误色，完成的用成功色）
        views.setTextViewText(R.id.todo_state, note.state)
        views.setTextColor(
            R.id.todo_state,
            if (note.isDone) palette.success else palette.error
        )

        // 标题（按层级缩进；完成态删除线 + 次级色，不靠降色保可读）
        val indent = "　".repeat((note.level - 1).coerceAtLeast(0))
        val title = indent + note.title
        views.setTextViewText(
            R.id.todo_title,
            if (note.isDone) {
                SpannableString(title).apply {
                    setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else {
                title
            }
        )
        views.setTextColor(
            R.id.todo_title,
            if (note.isDone) palette.secondary else palette.onSurface
        )

        // meta 行：tags + planning 时间
        val meta = buildList {
            if (note.tags.isNotEmpty()) add(note.tags.joinToString(" ") { ":$it:" })
            note.scheduled?.let { add("S:" + formatOrgTimestamp(it)) }
            note.deadline?.let { add("D:" + formatOrgTimestamp(it)) }
        }
        if (meta.isNotEmpty()) {
            views.setTextViewText(R.id.todo_meta, meta.joinToString(" "))
            views.setTextColor(R.id.todo_meta, palette.secondary)
            views.setViewVisibility(R.id.todo_meta, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.todo_meta, android.view.View.GONE)
        }

        // 点击切换状态：附条目身份指纹，外部修改行号平移后拒绝误写
        val fillInIntent = Intent().apply {
            putExtra(UsageWidgetProvider.EXTRA_LINE_INDEX, note.lineIndex)
            putExtra(UsageWidgetProvider.EXTRA_NOTE_STATE, note.state)
            putExtra(UsageWidgetProvider.EXTRA_NOTE_TITLE, note.title)
            note.priority?.let { putExtra(UsageWidgetProvider.EXTRA_NOTE_PRIORITY, it) }
            if (note.tags.isNotEmpty()) {
                putExtra(UsageWidgetProvider.EXTRA_NOTE_TAGS, note.tags.joinToString(","))
            }
        }
        views.setOnClickFillInIntent(R.id.widget_item_root, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false

    private suspend fun loadTodoNotes(): List<OrgNote> {
        return try {
            val database = AppUsageDatabase.getInstance(context)
            val repository = OrgTodoRepository(context, database.appSettingDao())
            when (val result = repository.readTodos()) {
                is OrgTodoRepository.ReadResult.Success -> filterNotes(result.file.notes)
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取 org 待办失败", e)
            emptyList()
        }
    }

    /** 将 org 时间戳原文转为简洁日期显示 */
    private fun formatOrgTimestamp(raw: String): String {
        val inner = raw.removePrefix("<").removePrefix("[").removeSuffix(">").removeSuffix("]")
        return inner.split(Regex("\\s+"))
            .filterIndexed { index, part ->
                index == 0 || part.matches(Regex("\\d{1,2}:\\d{2}(-\\d{1,2}:\\d{2})?"))
            }
            .joinToString(" ")
    }
}
