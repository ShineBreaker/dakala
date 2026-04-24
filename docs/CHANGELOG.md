# 更新日志 (Changelog)

本项目的所有重要更改都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
并且本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.0.6.1] - 2026-04-25

### 修复

- **使用时长跨日桶穿透**：修复 `INTERVAL_BEST` 模式下，周/月/年级别的跨日统计桶被误用为当日数据的问题。表现为未打开过的应用仍显示前一日甚至更早的使用时长，直到手动打开该应用后才恢复正常

### 技术细节

- 更新 `UsageStatsUseCase.kt` - `filterTodayStats()`、`getAllUsedAppsToday()`、`getAppsUsageDurationInRange()` 三个方法的桶过滤条件从"与目标范围有重叠即可"改为"桶必须完全落在查询范围内"，排除所有跨日大桶

## [1.0.6] - 2026-04-20

### 修复

- **跨天使用时长不刷新**：修复主界面在跨天后仍显示前一天使用时长的问题，Activity onResume 时自动检测日期变更并刷新数据
- **自定义打卡记录跨天不更新**：自定义打卡记录的日期查询改为响应式驱动，跨天后自动切换到当天记录

### 改进

- **Lifecycle API 升级**：将废弃的 `LocalLifecycleOwner` 替换为 `LifecycleResumeEffect`
- **构建环境兼容性**：`aapt2` wrapper 增加 Guix store 路径搜索，解决 non-FHS 系统上找不到 dynamic linker 和 libgcc 的问题
- **Maven 镜像配置**：添加阿里云 Maven 镜像，解决 `dl.google.com` 在部分网络环境下 TLS 握手失败的问题
- **IDE 警告清理**：修复未使用 import、冗余限定符、`Enum.values()` → `Enum.entries`、`String.format` Locale 等警告

### 技术细节

- 更新 `MainViewModel.kt` - 添加日期变更检测（`checkDateAndRefresh`），`customCheckStatusGroup` 改用 `_currentDate.flatMapLatest` 驱动
- 更新 `MainActivity.kt` - 使用 `LifecycleResumeEffect` 监听 onResume 事件
- 更新 `AppUsageRepository.kt` - 新增 `getCustomCheckRecordsByDateFlow(date)` 方法
- 更新 `settings.gradle.kts` - 添加阿里云 Maven 镜像仓库
- 更新 `tools/aapt2/aapt2` - 增强 `find_dynamic_linker` 和 `find_libgcc_dir` 的 Guix 兼容性
- 更新 `libs.versions.toml` - 添加 `lifecycle-runtime-compose` 依赖
- 更新 `app/build.gradle.kts` - 添加 `lifecycle-runtime-compose` 依赖，版本升级至 1.0.6

## [1.0.5] - 2026-03-19

### 新增

- **自定义打卡项**：新增自定义打卡能力，支持在应用内和小部件中展示与操作自定义打卡内容
- **自定义打卡小部件布局**：新增自定义打卡列表项样式与相关图标资源

### 改进

- **构建工具链升级**：升级到 Java 25，并将 Android 目标平台提升到 Android 16（API 36.1）
- **容器构建兼容性修复**：完善 `distrobox` 容器内的 `gradlew` 启动逻辑，自动修正 `JAVA_HOME` 和证书路径问题
- **依赖与注解处理升级**：更新 Kotlin、KSP、Hilt 及相关构建链路，确保新工具链下可正常编译
- **构建警告清理**：修复 Room、主题、小部件和图像处理中的废弃 API 警告，Debug/Release 构建输出更干净
- **文档更新**：补充容器化构建说明，并更新 JDK 与 Android SDK 版本要求

### 技术细节

- 更新 `app/build.gradle.kts` - 切换到 Java 25 / API 36.1，并调整打包配置
- 更新 `gradle/libs.versions.toml` - 升级 Kotlin、KSP、Hilt 与 AGP 相关依赖版本
- 更新 `build.gradle.kts` - 补充 Kotlin Gradle Plugin 兼容配置
- 更新 `gradlew` - 增强容器内构建环境自适应处理
- 更新 `README.md` - 补充 `distrobox` 容器内构建指南
- 更新多个 UI / Widget / 数据库文件 - 修复警告并完善自定义打卡功能

## [1.0.3.1] - 2026-03-08

### 新增

- **小部件手动刷新按钮**：在桌面小部件标题栏添加刷新按钮，点击即可立即刷新数据
- **WorkManager 定期刷新**：新增 `WidgetRefreshWorker`，每 15 分钟自动刷新小部件，比系统自带机制更可靠
- **屏幕解锁时自动刷新**：新增 `ScreenStateReceiver`，用户解锁屏幕时自动刷新小部件数据

### 改进

- **小部件刷新机制优化**：移除对 Android 系统 `updatePeriodMillis` 的依赖，改用 WorkManager 进行定期刷新，解决小部件长时间不自动刷新的问题
- **开机启动优化**：设备启动后立即刷新小部件，并调度定期刷新任务

### 技术细节

- 新增 `WidgetRefreshWorker.kt` - 小部件定期刷新 Worker
- 新增 `ScreenStateReceiver.kt` - 屏幕状态接收器
- 新增 `ic_refresh.xml` - 刷新按钮图标资源
- 更新 `UsageWidgetProvider.kt` - 添加手动刷新 Action 处理
- 更新 `BootReceiver.kt` - 添加小部件刷新任务调度
- 更新 `DakalaApplication.kt` - 应用启动时调度定期刷新任务
- 更新 `widget_usage.xml` - 添加刷新按钮布局

## [1.0.3] - 2026-03-06

### 新增

- 初始发布版本
- 应用监控功能：从系统应用列表中选择要监控的应用
- 状态展示：主界面显示已选应用列表、今日是否打开、今日打开时长
- 打卡提醒：设置时间点（如 22:00），自动发送通知提示未完成的应用
- 桌面小部件：快速查看未打开/时长不足的应用，点击可快速打开

[1.0.3.1]: https://github.com/user/dakala/compare/v1.0.3...v1.0.3.1
[1.0.3]: https://github.com/user/dakala/releases/tag/v1.0.3
[1.0.5]: https://github.com/user/dakala/compare/v1.0.3.1...v1.0.5
[1.0.6.1]: https://github.com/user/dakala/compare/v1.0.6...v1.0.6.1
[1.0.6]: https://github.com/user/dakala/compare/v1.0.5...v1.0.6
