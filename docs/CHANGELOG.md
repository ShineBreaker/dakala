# 更新日志 (Changelog)

本项目的所有重要更改都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
并且本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

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
