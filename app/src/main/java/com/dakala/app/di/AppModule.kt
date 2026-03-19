package com.dakala.app.di

import android.content.Context
import com.dakala.app.data.local.dao.AppItemDao
import com.dakala.app.data.local.dao.AppSettingDao
import com.dakala.app.data.local.dao.UsageRecordDao
import com.dakala.app.data.local.dao.CustomCheckItemDao
import com.dakala.app.data.local.dao.CustomCheckRecordDao
import com.dakala.app.data.local.database.AppUsageDatabase
import com.dakala.app.data.repository.AppUsageRepository
import com.dakala.app.domain.usecase.UsageStatsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据层依赖注入模块
 * 
 * 提供数据库、DAO和Repository的依赖注入配置。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /**
     * 提供数据库实例
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppUsageDatabase {
        return AppUsageDatabase.getInstance(context)
    }

    /**
     * 提供应用监控项DAO
     */
    @Provides
    @Singleton
    fun provideAppItemDao(database: AppUsageDatabase): AppItemDao {
        return database.appItemDao()
    }

    /**
     * 提供使用记录DAO
     */
    @Provides
    @Singleton
    fun provideUsageRecordDao(database: AppUsageDatabase): UsageRecordDao {
        return database.usageRecordDao()
    }

    /**
     * 提供应用设置DAO
     */
    @Provides
    @Singleton
    fun provideAppSettingDao(database: AppUsageDatabase): AppSettingDao {
        return database.appSettingDao()
    }

    /**
     * 提供自定义打卡项DAO
     */
    @Provides
    @Singleton
    fun provideCustomCheckItemDao(database: AppUsageDatabase): CustomCheckItemDao {
        return database.customCheckItemDao()
    }

    /**
     * 提供自定义打卡记录DAO
     */
    @Provides
    @Singleton
    fun provideCustomCheckRecordDao(database: AppUsageDatabase): CustomCheckRecordDao {
        return database.customCheckRecordDao()
    }

    /**
     * 提供应用使用统计仓库
     */
    @Provides
    @Singleton
    fun provideAppUsageRepository(
        appItemDao: AppItemDao,
        usageRecordDao: UsageRecordDao,
        appSettingDao: AppSettingDao,
        customCheckItemDao: CustomCheckItemDao,
        customCheckRecordDao: CustomCheckRecordDao
    ): AppUsageRepository {
        return AppUsageRepository(appItemDao, usageRecordDao, appSettingDao, customCheckItemDao, customCheckRecordDao)
    }
}

/**
 * 领域层依赖注入模块
 * 
 * 提供UseCase的依赖注入配置。
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    /**
     * 提供使用统计用例
     */
    @Provides
    @Singleton
    fun provideUsageStatsUseCase(
        @ApplicationContext context: Context
    ): UsageStatsUseCase {
        return UsageStatsUseCase(context)
    }
}