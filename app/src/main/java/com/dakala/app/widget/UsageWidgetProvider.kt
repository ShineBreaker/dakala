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
import com.dakala.app.data.local.entity.CustomCheckItem
import com.dakala.app.data.local.entity.CustomCheckRecord
import com.dakala.app.data.org.OrgNote
import com.dakala.app.data.repository.OrgTodoRepository
import com.dakala.app.domain.model.AppMonitorStatus
import com.dakala.app.domain.model.DayStamp
import com.dakala.app.domain.usecase.GetIncompleteAppsUseCase
import com.dakala.app.ui.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 应用使用状态桌面小部件
 *
 * 显示未完成打卡的应用列表和自定义打卡项，点击可快速打开对应应用或切换打卡状态。
 *
 * 功能特点：
 * 1. 显示未打开和时长不足的应用
 * 2. 显示自定义打卡项
 * 3. 点击应用项可直接打开应用
 * 4. 点击自定义打卡项可切换完成状态
 * 5. Tab 切换：应用打卡 / 自定义打卡
 * 6. 定期自动刷新（通过WorkManager，每15分钟）
 * 7. 支持手动刷新按钮
 * 8. 屏幕解锁时自动刷新
 */
class UsageWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "UsageWidgetProvider"

        const val ACTION_APP_CLICKED = "com.dakala.app.ACTION_APP_CLICKED"
        const val ACTION_REFRESH = "com.dakala.app.ACTION_REFRESH_WIDGET"
        const val ACTION_TAB_SWITCH = "com.dakala.app.ACTION_TAB_SWITCH"
        const val ACTION_CUSTOM_CHECK_TOGGLE = "com.dakala.app.ACTION_CUSTOM_CHECK_TOGGLE"
        const val ACTION_TODO_TOGGLE = "com.dakala.app.ACTION_TODO_TOGGLE"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_TAB_INDEX = "tab_index"
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_IS_COMPLETED = "is_completed"
        const val EXTRA_LINE_INDEX = "line_index"

        // SharedPreferences key for current tab
        private const val PREFS_NAME = "widget_prefs"
        private const val KEY_CURRENT_TAB = "current_tab"

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

        /**
         * 获取当前选中的 Tab
         */
        private fun getCurrentTab(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_CURRENT_TAB, 0)
        }

        /**
         * 设置当前选中的 Tab
         */
        private fun setCurrentTab(context: Context, tabIndex: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_CURRENT_TAB, tabIndex).apply()
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate: 更新 ${appWidgetIds.size} 个小部件")

        // 使用 runBlocking 同步更新小部件
        kotlinx.coroutines.runBlocking {
            try {
                updateAllWidgets(context, appWidgetManager, appWidgetIds)
            } catch (e: Exception) {
                Log.e(TAG, "更新小部件失败", e)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        Log.d(TAG, "onAppWidgetOptionsChanged: 小部件 $appWidgetId 选项已更改")
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
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
            ACTION_TAB_SWITCH -> {
                // 处理 Tab 切换事件
                val tabIndex = intent.getIntExtra(EXTRA_TAB_INDEX, 0)
                setCurrentTab(context, tabIndex)
                Log.d(TAG, "onReceive: 切换到 Tab $tabIndex")

                // 刷新小部件
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, UsageWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        updateAllWidgets(context, appWidgetManager, appWidgetIds)
                    } catch (e: Exception) {
                        Log.e(TAG, "Tab 切换刷新小部件失败", e)
                    }
                }
            }
            ACTION_CUSTOM_CHECK_TOGGLE -> {
                // 处理自定义打卡切换事件
                val itemId = intent.getIntExtra(EXTRA_ITEM_ID, -1)
                val isCompleted = intent.getBooleanExtra(EXTRA_IS_COMPLETED, false)
                if (itemId >= 0) {
                    toggleCustomCheckStatus(context, itemId, !isCompleted)
                }
            }
            ACTION_TODO_TOGGLE -> {
                // 处理待办状态切换事件
                val lineIndex = intent.getIntExtra(EXTRA_LINE_INDEX, -1)
                if (lineIndex >= 0) {
                    toggleTodoNote(context, lineIndex)
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
     * 切换自定义打卡状态
     */
    private fun toggleCustomCheckStatus(context: Context, itemId: Int, isCompleted: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppUsageDatabase.getInstance(context)
                val customCheckRecordDao = database.customCheckRecordDao()

                val today = DayStamp.today()
                val record = CustomCheckRecord(
                    itemId = itemId,
                    date = today,
                    isCompleted = isCompleted,
                    completedAt = if (isCompleted) System.currentTimeMillis() else null
                )
                customCheckRecordDao.insertOrUpdateRecord(record)

                Log.d(TAG, "切换自定义打卡状态: itemId=$itemId, isCompleted=$isCompleted")

                // 刷新小部件
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, UsageWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                updateAllWidgets(context, appWidgetManager, appWidgetIds)
            } catch (e: Exception) {
                Log.e(TAG, "切换自定义打卡状态失败", e)
            }
        }
    }

    /**
     * 切换 org 待办状态（写回 org 文件）
     */
    private fun toggleTodoNote(context: Context, lineIndex: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppUsageDatabase.getInstance(context)
                val repository = OrgTodoRepository(context, database.appSettingDao())
                repository.toggleNoteAtLine(lineIndex)
                Log.d(TAG, "切换待办状态: lineIndex=$lineIndex")

                // 刷新小部件
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, UsageWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                updateAllWidgets(context, appWidgetManager, appWidgetIds)
            } catch (e: Exception) {
                Log.e(TAG, "切换待办状态失败", e)
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
        Log.d(TAG, "updateAllWidgets: 开始更新 ${appWidgetIds.size} 个小部件")
        val currentTab = getCurrentTab(context)
        Log.d(TAG, "updateAllWidgets: 当前 Tab = $currentTab")

        // 检查权限
        val hasPermission = PermissionHelper.checkUsageStatsPermission(context)
        Log.d(TAG, "updateAllWidgets: 权限检查 = $hasPermission")

        if (!hasPermission && currentTab == 0) {
            Log.d(TAG, "updateAllWidgets: 无权限，显示权限提示")
            // 无权限时显示提示
            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_usage)
                views.setTextViewText(R.id.widget_title, "需要权限")
                views.setTextViewText(R.id.widget_empty, "请打开应用授权")
                views.setViewVisibility(R.id.widget_list, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                Log.d(TAG, "updateAllWidgets: 已更新小部件 $appWidgetId (无权限提示)")
            }
            return
        }

        // 应用打卡列表只查询一次，避免每个 widget id 重复取数
        val incompleteApps = if (currentTab == 0) {
            GetIncompleteAppsUseCase(context)()
        } else {
            emptyList()
        }

        // 更新每个小部件
        for (appWidgetId in appWidgetIds) {
            Log.d(TAG, "updateAllWidgets: 更新小部件 $appWidgetId, Tab=$currentTab")
            when (currentTab) {
                0 -> {
                    // 应用打卡 Tab
                    Log.d(TAG, "updateAllWidgets: 获取到 ${incompleteApps.size} 个未完成应用")
                    updateAppCheckWidget(context, appWidgetManager, appWidgetId, incompleteApps)
                }
                1 -> {
                    // 自定义打卡 Tab
                    val customCheckItems = getCustomCheckItems(context)
                    Log.d(TAG, "updateAllWidgets: 获取到 ${customCheckItems.size} 个自定义打卡项")
                    updateCustomCheckWidget(context, appWidgetManager, appWidgetId, customCheckItems)
                }
                else -> {
                    // Org 待办 Tab
                    updateTodoWidget(context, appWidgetManager, appWidgetId)
                }
            }
        }
        Log.d(TAG, "updateAllWidgets: 完成更新")
    }

    /**
     * 更新应用打卡小部件
     */
    @Suppress("DEPRECATION")
    private fun updateAppCheckWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        incompleteApps: List<AppMonitorStatus>
    ) {
        Log.d(TAG, "updateAppCheckWidget: 开始更新小部件 $appWidgetId, 未完成应用数=${incompleteApps.size}")

        val views = RemoteViews(context.packageName, R.layout.widget_usage)
        Log.d(TAG, "updateAppCheckWidget: 创建 RemoteViews 成功")

        // 设置 Tab 按钮状态
        setupTabButtons(context, views, appWidgetId, 0)
        Log.d(TAG, "updateAppCheckWidget: 设置 Tab 按钮完成")

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
        Log.d(TAG, "updateAppCheckWidget: 设置标题点击事件完成")

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
        Log.d(TAG, "updateAppCheckWidget: 设置刷新按钮完成")

        if (incompleteApps.isEmpty()) {
            Log.d(TAG, "updateAppCheckWidget: 应用列表为空，显示空状态")
            // 所有应用都已完成
            views.setViewVisibility(R.id.widget_list, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_all_completed))
        } else {
            Log.d(TAG, "updateAppCheckWidget: 设置 RemoteAdapter")
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
            Log.d(TAG, "updateAppCheckWidget: 设置点击模板完成")

            // 设置列表适配器 - WidgetService 会直接查询数据库
            val intent = Intent(context, WidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra("tab_type", 0) // 应用打卡
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list, intent)
            Log.d(TAG, "updateAppCheckWidget: 设置 RemoteAdapter 完成")
        }

        Log.d(TAG, "updateAppCheckWidget: 调用 updateAppWidget")
        appWidgetManager.updateAppWidget(appWidgetId, views)
        Log.d(TAG, "updateAppCheckWidget: updateAppWidget 完成")

        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
        Log.d(TAG, "updateAppCheckWidget: notifyAppWidgetViewDataChanged 完成")
    }

    /**
     * 更新自定义打卡小部件
     */
    @Suppress("DEPRECATION")
    private fun updateCustomCheckWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        customCheckItems: List<CustomCheckItemWithStatus>
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_usage)

        // 设置 Tab 按钮状态
        setupTabButtons(context, views, appWidgetId, 1)

        // 设置点击标题跳转到本应用
        val mainIntent = Intent(context, com.dakala.app.ui.MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            1,
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
            appWidgetId + 1000,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)

        if (customCheckItems.isEmpty()) {
            // 没有自定义打卡项
            views.setViewVisibility(R.id.widget_list, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_empty, "暂无自定义打卡项")
        } else {
            // 显示自定义打卡列表
            views.setViewVisibility(R.id.widget_list, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)

            // 设置列表项点击模板
            val checkClickIntent = Intent(context, UsageWidgetProvider::class.java).apply {
                action = ACTION_CUSTOM_CHECK_TOGGLE
            }
            val checkClickPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 2000,
                checkClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, checkClickPendingIntent)

            // 设置列表适配器
            val intent = Intent(context, WidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra("tab_type", 1) // 自定义打卡
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list, intent)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
    }

    /**
     * 更新 Org 待办小部件
     */
    @Suppress("DEPRECATION")
    private suspend fun updateTodoWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_usage)

        // 设置 Tab 按钮状态
        setupTabButtons(context, views, appWidgetId, 2)

        // 设置点击标题跳转到本应用
        val mainIntent = Intent(context, com.dakala.app.ui.MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            2,
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
            appWidgetId + 1000,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)

        // 读取 org 文件待办
        val database = AppUsageDatabase.getInstance(context)
        val repository = OrgTodoRepository(context, database.appSettingDao())
        when (val result = repository.readTodos()) {
            is OrgTodoRepository.ReadResult.NoFile -> {
                views.setViewVisibility(R.id.widget_list, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
                views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_no_org_file))
            }
            is OrgTodoRepository.ReadResult.Error -> {
                views.setViewVisibility(R.id.widget_list, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
                views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_no_org_file))
            }
            is OrgTodoRepository.ReadResult.Success -> {
                if (result.file.notes.isEmpty()) {
                    views.setViewVisibility(R.id.widget_list, android.view.View.GONE)
                    views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
                    views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_todo_empty))
                } else {
                    views.setViewVisibility(R.id.widget_list, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)

                    // 设置列表项点击模板
                    val todoClickIntent = Intent(context, UsageWidgetProvider::class.java).apply {
                        action = ACTION_TODO_TOGGLE
                    }
                    val todoClickPendingIntent = PendingIntent.getBroadcast(
                        context,
                        appWidgetId + 6000,
                        todoClickIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setPendingIntentTemplate(R.id.widget_list, todoClickPendingIntent)

                    // 设置列表适配器
                    val intent = Intent(context, WidgetService::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        putExtra("tab_type", 2) // Org 待办
                        data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                    }
                    views.setRemoteAdapter(R.id.widget_list, intent)
                }
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
    }

    /**
     * 设置 Tab 按钮状态
     */
    private fun setupTabButtons(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        currentTab: Int
    ) {
        // 应用打卡 Tab
        val appTabIntent = Intent(context, UsageWidgetProvider::class.java).apply {
            action = ACTION_TAB_SWITCH
            putExtra(EXTRA_TAB_INDEX, 0)
        }
        val appTabPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId + 3000,
            appTabIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_tab_app, appTabPendingIntent)
        views.setBoolean(R.id.btn_tab_app, "setEnabled", currentTab != 0)

        // 自定义打卡 Tab
        val customTabIntent = Intent(context, UsageWidgetProvider::class.java).apply {
            action = ACTION_TAB_SWITCH
            putExtra(EXTRA_TAB_INDEX, 1)
        }
        val customTabPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId + 4000,
            customTabIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_tab_custom, customTabPendingIntent)
        views.setBoolean(R.id.btn_tab_custom, "setEnabled", currentTab != 1)

        // 待办 Tab
        val todoTabIntent = Intent(context, UsageWidgetProvider::class.java).apply {
            action = ACTION_TAB_SWITCH
            putExtra(EXTRA_TAB_INDEX, 2)
        }
        val todoTabPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId + 5000,
            todoTabIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_tab_todo, todoTabPendingIntent)
        views.setBoolean(R.id.btn_tab_todo, "setEnabled", currentTab != 2)
    }

    /**
     * 获取自定义打卡项列表
     */
    private suspend fun getCustomCheckItems(context: Context): List<CustomCheckItemWithStatus> {
        val database = AppUsageDatabase.getInstance(context)
        val customCheckItemDao = database.customCheckItemDao()
        val customCheckRecordDao = database.customCheckRecordDao()

        // 获取所有自定义打卡项
        val items = customCheckItemDao.getAllItems()

        // 获取今日打卡记录
        val today = DayStamp.today()
        val records = customCheckRecordDao.getRecordsByDate(today)
        val completedItemIds = records.filter { it.isCompleted }.map { it.itemId }.toSet()

        return items.map { item ->
            CustomCheckItemWithStatus(
                item = item,
                isCompleted = item.id in completedItemIds
            )
        }
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
     * 自定义打卡项状态数据类
     */
    data class CustomCheckItemWithStatus(
        val item: CustomCheckItem,
        val isCompleted: Boolean
    )
}
