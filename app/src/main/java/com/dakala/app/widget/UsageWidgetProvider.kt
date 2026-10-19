package com.dakala.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.dakala.app.R
import com.dakala.app.data.local.database.AppUsageDatabase
import com.dakala.app.data.local.entity.AppSetting
import com.dakala.app.data.local.entity.CustomCheckItem
import com.dakala.app.data.local.entity.CustomCheckRecord
import com.dakala.app.data.org.OrgNote
import com.dakala.app.data.repository.OrgTodoRepository
import com.dakala.app.domain.model.AppMonitorStatus
import com.dakala.app.domain.model.DayStamp
import com.dakala.app.domain.model.WidgetOpacity
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
 * 9. 深浅色切换时经 CONFIGURATION_CHANGED 重绘配色
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

        // 上次重绘时的系统明暗位，用于过滤非主题类配置变化
        private const val KEY_LAST_NIGHT = "last_night_mode"

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
            // 缺省落在待办 Tab（Tab 顺序：待办/应用打卡/自定义打卡，索引语义不变）
            return prefs.getInt(KEY_CURRENT_TAB, 2)
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
                refreshAllWidgets(context)
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
                refreshAllWidgets(context)
            }
            Intent.ACTION_CONFIGURATION_CHANGED -> {
                // 系统配置变化：仅在明暗位变化时重绘（旋转/字体等不重绘）
                val nightNow = WidgetPalette.isNightMode(context)
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val nightBefore = prefs.getBoolean(KEY_LAST_NIGHT, nightNow)
                if (nightNow != nightBefore) {
                    prefs.edit().putBoolean(KEY_LAST_NIGHT, nightNow).apply()
                    Log.d(TAG, "onReceive: 深浅色切换 night=$nightNow，重绘小部件")
                    refreshAllWidgets(context)
                }
            }
        }
    }

    /**
     * 后台协程刷新全部小部件实例
     */
    private fun refreshAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, UsageWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateAllWidgets(context, appWidgetManager, appWidgetIds)
            } catch (e: Exception) {
                Log.e(TAG, "刷新小部件失败", e)
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
        val dao = AppUsageDatabase.getInstance(context).appSettingDao()
        val opacity = WidgetOpacity.parse(dao.getSetting(AppSetting.KEY_WIDGET_OPACITY))
        val effectiveTab = currentTab
        Log.d(TAG, "updateAllWidgets: 当前 Tab = $effectiveTab, 不透明度 = ${opacity.percent}%")

        // 检查权限
        val hasPermission = PermissionHelper.checkUsageStatsPermission(context)
        Log.d(TAG, "updateAllWidgets: 权限检查 = $hasPermission")

        if (!hasPermission && effectiveTab == 0) {
            Log.d(TAG, "updateAllWidgets: 无权限，显示权限提示")
            // 无权限时显示提示
            for (appWidgetId in appWidgetIds) {
                val views = createRemoteViews(context) { v, palette ->
                    applyBaseTheme(v, palette, opacity)
                    setupTabButtons(context, v, appWidgetId, 0, palette)
                    v.setTextViewText(R.id.widget_title, "需要权限")
                    v.setTextViewText(R.id.widget_empty, "请打开应用授权")
                    v.setViewVisibility(R.id.widget_list, android.view.View.GONE)
                    v.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
                Log.d(TAG, "updateAllWidgets: 已更新小部件 $appWidgetId (无权限提示)")
            }
            return
        }

        // 应用打卡列表只查询一次，避免每个 widget id 重复取数
        val incompleteApps = if (effectiveTab == 0) {
            GetIncompleteAppsUseCase(context)()
        } else {
            emptyList()
        }

        // 更新每个小部件
        for (appWidgetId in appWidgetIds) {
            Log.d(TAG, "updateAllWidgets: 更新小部件 $appWidgetId, Tab=$effectiveTab")
            when (effectiveTab) {
                0 -> {
                    // 应用打卡 Tab
                    Log.d(TAG, "updateAllWidgets: 获取到 ${incompleteApps.size} 个未完成应用")
                    updateAppCheckWidget(context, appWidgetManager, appWidgetId, incompleteApps, opacity)
                }
                1 -> {
                    // 自定义打卡 Tab
                    val customCheckItems = getCustomCheckItems(context)
                    Log.d(TAG, "updateAllWidgets: 获取到 ${customCheckItems.size} 个自定义打卡项")
                    updateCustomCheckWidget(context, appWidgetManager, appWidgetId, customCheckItems, opacity)
                }
                else -> {
                    // Org 待办 Tab
                    updateTodoWidget(context, appWidgetManager, appWidgetId, opacity)
                }
            }
        }
        Log.d(TAG, "updateAllWidgets: 完成更新")
    }

    /**
     * 构建 RemoteViews
     *
     * 按当前系统明暗模式取一次调色板、把色值烘焙进动作列表。
     * 系统切换深浅色时经 CONFIGURATION_CHANGED 广播触发整部件重绘
     * （见 docs/DESIGN.md 第 2 节）；RemoteViews(landscape, portrait)
     * 构造器是尺寸双视图而非明暗双视图，不能用于主题切换。
     */
    private fun createRemoteViews(
        context: Context,
        configure: (views: RemoteViews, palette: WidgetPalette) -> Unit
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_usage).apply {
            configure(this, WidgetPalette.current(context))
        }
    }

    /**
     * 应用基础配色与背景透明度
     *
     * 背景层 ImageView 提供圆角形状蒙版：setColorFilter 染成 background
     * （surface 极端化版本），setImageAlpha 应用用户设置的不透明度；
     * 内容层保持不透明保证可读性。
     */
    private fun applyBaseTheme(views: RemoteViews, palette: WidgetPalette, opacity: WidgetOpacity) {
        views.setInt(R.id.widget_bg, "setColorFilter", palette.background)
        views.setInt(R.id.widget_bg, "setImageAlpha", opacity.toAlpha())
        views.setTextColor(R.id.widget_title, palette.onSurface)
        views.setTextColor(R.id.widget_empty, palette.secondary)
        views.setInt(R.id.btn_refresh, "setColorFilter", palette.secondary)
    }

    /**
     * 更新应用打卡小部件
     */
    @Suppress("DEPRECATION")
    private fun updateAppCheckWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        incompleteApps: List<AppMonitorStatus>,
        opacity: WidgetOpacity
    ) {
        Log.d(TAG, "updateAppCheckWidget: 开始更新小部件 $appWidgetId, 未完成应用数=${incompleteApps.size}")

        val views = createRemoteViews(context) { v, palette ->
            applyBaseTheme(v, palette, opacity)

            // 设置 Tab 按钮状态
            setupTabButtons(context, v, appWidgetId, 0, palette)

            // 设置点击标题跳转到本应用
            val mainIntent = Intent(context, com.dakala.app.ui.MainActivity::class.java)
            val mainPendingIntent = PendingIntent.getActivity(
                context,
                0,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.widget_title, mainPendingIntent)
            v.setOnClickPendingIntent(R.id.widget_empty, mainPendingIntent)

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
            v.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)

            if (incompleteApps.isEmpty()) {
                Log.d(TAG, "updateAppCheckWidget: 应用列表为空，显示空状态")
                // 所有应用都已完成
                v.setViewVisibility(R.id.widget_list, android.view.View.GONE)
                v.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
                v.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_all_completed))
            } else {
                Log.d(TAG, "updateAppCheckWidget: 设置 RemoteAdapter")
                // 显示未完成的应用列表
                v.setViewVisibility(R.id.widget_list, android.view.View.VISIBLE)
                v.setViewVisibility(R.id.widget_empty, android.view.View.GONE)

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
                v.setPendingIntentTemplate(R.id.widget_list, appClickPendingIntent)

                // 设置列表适配器 - WidgetService 会直接查询数据库
                val intent = Intent(context, WidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra("tab_type", 0) // 应用打卡
                    data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                v.setRemoteAdapter(R.id.widget_list, intent)
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
        Log.d(TAG, "updateAppCheckWidget: updateAppWidget 完成")
    }

    /**
     * 更新自定义打卡小部件
     */
    @Suppress("DEPRECATION")
    private fun updateCustomCheckWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        customCheckItems: List<CustomCheckItemWithStatus>,
        opacity: WidgetOpacity
    ) {
        val views = createRemoteViews(context) { v, palette ->
            applyBaseTheme(v, palette, opacity)

            // 设置 Tab 按钮状态
            setupTabButtons(context, v, appWidgetId, 1, palette)

            // 设置点击标题跳转到本应用
            val mainIntent = Intent(context, com.dakala.app.ui.MainActivity::class.java)
            val mainPendingIntent = PendingIntent.getActivity(
                context,
                1,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.widget_title, mainPendingIntent)
            v.setOnClickPendingIntent(R.id.widget_empty, mainPendingIntent)

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
            v.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)

            if (customCheckItems.isEmpty()) {
                // 没有自定义打卡项
                v.setViewVisibility(R.id.widget_list, android.view.View.GONE)
                v.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
                v.setTextViewText(R.id.widget_empty, "暂无自定义打卡项")
            } else {
                // 显示自定义打卡列表
                v.setViewVisibility(R.id.widget_list, android.view.View.VISIBLE)
                v.setViewVisibility(R.id.widget_empty, android.view.View.GONE)

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
                v.setPendingIntentTemplate(R.id.widget_list, checkClickPendingIntent)

                // 设置列表适配器
                val intent = Intent(context, WidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra("tab_type", 1) // 自定义打卡
                    data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                v.setRemoteAdapter(R.id.widget_list, intent)
            }
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
        appWidgetId: Int,
        opacity: WidgetOpacity
    ) {
        // 读取 org 文件待办
        val database = AppUsageDatabase.getInstance(context)
        val repository = OrgTodoRepository(context, database.appSettingDao())
        val result = repository.readTodos()

        val views = createRemoteViews(context) { v, palette ->
            applyBaseTheme(v, palette, opacity)

            // 设置 Tab 按钮状态
            setupTabButtons(context, v, appWidgetId, 2, palette)

            // 设置点击标题跳转到本应用
            val mainIntent = Intent(context, com.dakala.app.ui.MainActivity::class.java)
            val mainPendingIntent = PendingIntent.getActivity(
                context,
                2,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.widget_title, mainPendingIntent)
            v.setOnClickPendingIntent(R.id.widget_empty, mainPendingIntent)

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
            v.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)

            when (result) {
                is OrgTodoRepository.ReadResult.NoFile -> {
                    v.setViewVisibility(R.id.widget_list, android.view.View.GONE)
                    v.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
                    v.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_no_org_file))
                }
                is OrgTodoRepository.ReadResult.Error -> {
                    v.setViewVisibility(R.id.widget_list, android.view.View.GONE)
                    v.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
                    v.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_no_org_file))
                }
                is OrgTodoRepository.ReadResult.Success -> {
                    if (result.file.notes.isEmpty()) {
                        v.setViewVisibility(R.id.widget_list, android.view.View.GONE)
                        v.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
                        v.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_todo_empty))
                    } else {
                        v.setViewVisibility(R.id.widget_list, android.view.View.VISIBLE)
                        v.setViewVisibility(R.id.widget_empty, android.view.View.GONE)

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
                        v.setPendingIntentTemplate(R.id.widget_list, todoClickPendingIntent)

                        // 设置列表适配器
                        val intent = Intent(context, WidgetService::class.java).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            putExtra("tab_type", 2) // Org 待办
                            data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                        }
                        v.setRemoteAdapter(R.id.widget_list, intent)
                    }
                }
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
    }

    /**
     * 设置 Tab 按钮状态
     *
     * 选中态仅用 accent 色文字区分，无底框。
     * 注意 setSelected 不是 @RemotableViewMethod，RemoteViews 不允许调用。
     */
    private fun setupTabButtons(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        currentTab: Int,
        palette: WidgetPalette
    ) {
        listOf(R.id.btn_tab_app, R.id.btn_tab_custom, R.id.btn_tab_todo).forEachIndexed { index, viewId ->
            val intent = Intent(context, UsageWidgetProvider::class.java).apply {
                action = ACTION_TAB_SWITCH
                putExtra(EXTRA_TAB_INDEX, index)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 3000 + index * 1000,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(viewId, pendingIntent)

            views.setTextColor(viewId, if (index == currentTab) palette.accent else palette.secondary)
        }
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
