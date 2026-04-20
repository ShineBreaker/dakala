# AGENTS.md — Dakala (打卡啦) 项目知识库

> Android 日常打卡提醒应用。监测应用使用时长，在设定时间提醒未完成打卡。

## 构建命令

```bash
# Debug 构建（必须加 --no-configuration-cache）
./gradlew assembleDebug --no-configuration-cache

# Release 构建（需要 keystore.properties + dakala.keystore）
./gradlew assembleRelease --no-configuration-cache

# 测试
./gradlew test                    # 单元测试（JUnit 4）
./gradlew connectedAndroidTest   # 设备测试（Espresso + Compose UI Test）

# 代码检查
./gradlew ktlintCheck            # Kotlin 代码风格
./gradlew lint                    # Android Lint（abortOnError=false）
```

### 构建注意事项

- **`--no-configuration-cache` 是必须的**，虽然 `gradle.properties` 设置了 `org.gradle.configuration-cache=true`，但实际构建因 AGP/WorkManager 插件兼容性问题必须禁用。
- **JDK 25**（OpenJDK 25），AGP 9.0.1，compileSdk 36.1，minSdk 29，targetSdk 36。
- **自定义 aapt2**：`tools/aapt2/aapt2` 是为 Guix/Nix 等 non-FHS Linux 打包的 wrapper，`gradlew` 自动导出 `aapt2FromMavenOverride` 环境变量。在标准 Linux 发行版上构建不影响。
- Release 签名文件（`keystore.properties`、`dakala.keystore`）已 gitignored，本地构建 debug 不需要。

## 项目结构

单模块 `:app`，MVVM + Clean Architecture：

```
app/src/main/java/com/dakala/app/
├── data/
│   ├── local/
│   │   ├── dao/          # Room DAO 接口
│   │   ├── database/     # AppDatabase (Room v3, fallbackToDestructiveMigration)
│   │   └── entity/       # Room 实体
│   └── repository/       # Repository 实现
├── di/
│   └── AppModule.kt      # @Module: DataModule + DomainModule（Hilt）
├── domain/
│   ├── model/            # 领域模型（data class）
│   └── usecase/          # 用例（单职责，@Inject constructor）
├── ui/
│   ├── components/       # 可复用 Compose 组件
│   ├── theme/            # Material 3 主题（Color.kt, Theme.kt, Type.kt）
│   ├── util/             # UI 工具类
│   ├── viewmodel/        # ViewModel 层
│   ├── MainActivity.kt   # 主界面（@AndroidEntryPoint）
│   └── AppSelectionActivity.kt  # 应用选择界面
├── widget/
│   ├── UsageWidgetProvider.kt  # 桌面小组件
│   └── WidgetService.kt        # RemoteViewsService
├── worker/
│   ├── NotificationWorker.kt   # 通知推送（WorkManager）
│   └── WidgetRefreshWorker.kt  # 小组件刷新
├── receiver/
│   ├── BootReceiver.kt         # 开机自启
│   └── ScreenStateReceiver.kt  # 屏幕状态监听
└── DakalaApplication.kt        # @HiltAndroidApp，WorkManager 自定义初始化
```

## 代码风格

- **ktlint 全局强制**：root `build.gradle.kts` 的 `subprojects` 块配置，`android=true`，`outputColorName=RED`。
- `kotlin.code.style=official`（gradle.properties）。
- 无 `.editorconfig`，无 detekt。
- 提交前确保 `./gradlew ktlintCheck` 通过。

## 技术约束

| 约束 | 说明 |
|------|------|
| 完全离线 | 无网络层，无 Retrofit/OkHttp，无 API 密钥 |
| PACKAGE_USAGE_STATS | 系统「使用情况访问」权限，无法代码请求，需引导用户到系统设置手动开启 |
| Room 主线程查询 | `allowMainThreadQueries=true`（简单场景刻意为之） |
| Room 迁移 | `fallbackToDestructiveMigration()`（Schema 变更时数据不保留） |
| Lint 宽松 | `abortOnError=false, checkReleaseBuilds=false` |

## 依赖管理

版本目录：`gradle/libs.versions.toml`。添加新依赖时在此文件统一管理版本。

## 测试

当前仅 `app/src/test/` 下有少量单元测试（JUnit 4）。`app/src/androidTest/` 有 Espresso + Compose UI 测试框架配置。

## 文档

- `docs/BUILD.md` — 构建指南（含 Guix 环境特殊说明）
- `docs/GUIX_ANDROID_BUILD.md` — Guix 系统上构建 Android 的详细文档
- `docs/CHANGELOG.md` — 版本变更日志
