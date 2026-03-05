package com.dakala.app.ui.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import com.dakala.app.R

/**
 * 权限检查和引导工具类
 * 
 * 用于检查和引导用户开启PACKAGE_USAGE_STATS权限。
 * 该权限无法通过代码直接请求，必须引导用户到系统设置中手动开启。
 * 
 * 重要说明：
 * - PACKAGE_USAGE_STATS是系统级权限，需要用户手动授权
 * - 从Android 5.0 (API 21)开始提供
 * - 用于获取应用使用统计数据
 */
object PermissionHelper {

    /**
     * 检查是否已获取使用统计权限
     * 
     * 通过AppOpsManager检查当前应用是否有PACKAGE_USAGE_STATS权限。
     * 
     * @param context 应用上下文
     * @return 是否已获取权限
     */
    fun checkUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * 打开使用统计权限设置页面
     * 
     * 引导用户到系统设置中开启PACKAGE_USAGE_STATS权限。
     * 不同Android版本可能需要不同的设置路径。
     * 
     * @param context 应用上下文
     */
    fun openUsageStatsSettings(context: Context) {
        try {
            // 尝试直接打开应用详情页
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                // 添加包名参数，部分系统可以直接跳转到当前应用的设置
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 如果直接打开失败，尝试打开应用设置页
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // 最后尝试打开设置主页
                try {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        R.string.permission_denied,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * 显示权限引导提示
     * 
     * 当用户未授权时，显示Toast提示用户如何开启权限。
     * 
     * @param context 应用上下文
     */
    fun showPermissionGuide(context: Context) {
        Toast.makeText(
            context,
            R.string.usage_permission_message,
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * 检查权限并引导用户开启
     * 
     * 如果未授权，显示提示并打开设置页面。
     * 
     * @param context 应用上下文
     * @return 是否已授权
     */
    fun checkAndRequestPermission(context: Context): Boolean {
        if (!checkUsageStatsPermission(context)) {
            showPermissionGuide(context)
            openUsageStatsSettings(context)
            return false
        }
        return true
    }
}