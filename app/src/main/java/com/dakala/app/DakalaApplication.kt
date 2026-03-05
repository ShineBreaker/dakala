package com.dakala.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.dakala.app.worker.NotificationWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 应用入口类
 * 
 * 负责初始化应用级别的组件，包括：
 * 1. Hilt依赖注入
 * 2. WorkManager配置
 * 3. 初始化通知调度
 */
@HiltAndroidApp
class DakalaApplication : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "DakalaApplication"
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "应用启动")
        
        // 初始化WorkManager
        WorkManager.initialize(this, workManagerConfiguration)
        
        // 调度默认通知（22:00）
        NotificationWorker.scheduleDailyNotification(this)
    }
}