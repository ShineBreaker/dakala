# AGENTS.md — Dakala (打卡啦) 项目知识库

> Android 日常打卡提醒应用。监测应用使用时长，在设定时间提醒未完成打卡。

## 构建命令

```bash
# Debug 构建（所有命令统一加 --no-configuration-cache）
./gradlew assembleDebug --no-configuration-cache

# Release 构建（需要 keystore.properties + dakala.keystore）
./gradlew assembleRelease --no-configuration-cache

# 测试（仅 JVM 单元测试，JUnit 4；无 androidTest 源集）
./gradlew test --no-configuration-cache

# 代码检查
./gradlew ktlintCheck --no-configuration-cache   # Kotlin 代码风格
./gradlew lint --no-configuration-cache          # Android Lint（abortOnError=false）
```

### 构建注意事项

- **统一加 `--no-configuration-cache`**：`gradle.properties` 设置了 `org.gradle.configuration-cache=true`，但构建历史上因 AGP/WorkManager 插件兼容性问题要求禁用；实测缓存命中时不带该参数也能通过，但 clean build 未验证，故所有构建与检查命令统一带上。
- **JDK 25**（OpenJDK 25），AGP 9.1.1，compileSdk 37.2（material3 1.5 要求 ≥37），minSdk 29，targetSdk 36。
- **自定义 aapt2**：`tools/aapt2/aapt2` 是为 Guix/Nix 等 non-FHS Linux 打包的 wrapper，`gradlew` 通过 `GRADLE_OPTS` 传入系统属性 `-Dorg.gradle.project.android.aapt2FromMavenOverride` 指向它。在标准 Linux 发行版上构建不影响。
- Release 签名文件（`keystore.properties`、`dakala.keystore`）已 gitignored，本地构建 debug 不需要。

## 项目结构

单模块 `:app`，MVVM + Clean Architecture：

```
app/src/main/java/com/dakala/app/
├── data/
│   ├── local/
│   │   ├── dao/          # Room DAO 接口
│   │   ├── database/     # AppUsageDatabase (Room v4, fallbackToDestructiveMigration)
│   │   └── entity/       # Room 实体
│   ├── org/              # org 文件解析与写回（OrgParser、OrgWriter、OrgSyntax）
│   └── repository/       # Repository 实现
├── di/
│   └── AppModule.kt      # @Module: DataModule + DomainModule（Hilt）
├── domain/
│   ├── model/            # 领域模型（data class）
│   └── usecase/          # 用例（单职责，@Inject constructor）
├── ui/
│   ├── components/       # 可复用 Compose 组件
│   ├── theme/            # Material 3 主题（Theme.kt, Typography.kt）
│   ├── util/             # UI 工具类
│   ├── viewmodel/        # ViewModel 层
│   ├── MainActivity.kt   # 主界面（@AndroidEntryPoint）
│   ├── SettingsActivity.kt    # 设置页（@AndroidEntryPoint）
│   └── AppSelectionActivity.kt  # 应用选择界面
├── widget/
│   ├── UsageWidgetProvider.kt  # 桌面小组件
│   ├── WidgetPalette.kt        # 小部件调色板（ColorScheme → ARGB）
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
- 提交前确保 `./gradlew ktlintCheck --no-configuration-cache` 通过。

## 改进流程

改代码前先读 `docs/CONTRIBUTING.md`（分区审计 → 独立复核 → 最小实施 → 验收 → 门禁 → 提交）。

- **门禁**：`./gradlew ktlintCheck test --no-configuration-cache` 必须绿才提交；交付前加跑 `./gradlew assembleDebug --no-configuration-cache`。
- **提交**：约定式提交；类型表来源为本机 `~/.config/git/gitmessage`，格式（header/body/署名）见 `docs/CONTRIBUTING.md`。

## 技术约束

| 约束 | 说明 |
|------|------|
| 完全离线 | 无网络层，无 Retrofit/OkHttp，无 API 密钥 |
| PACKAGE_USAGE_STATS | 系统「使用情况访问」权限，无法代码请求，需引导用户到系统设置手动开启 |
| Room 主线程查询 | `allowMainThreadQueries=true`（简单场景刻意为之） |
| Room 迁移 | Schema 变更**必须配套显式 `Migration`**（已有 `MIGRATION_3_4`）；`fallbackToDestructiveMigration()` 仅作未映射版本跳跃的兜底，不能替代迁移——v1.2.0 曾因纯 fallback 删光用户数据 |
| Lint 宽松 | `abortOnError=false, checkReleaseBuilds=false` |

## 依赖管理

版本目录：`gradle/libs.versions.toml`。添加新依赖时在此文件统一管理版本。

## 测试

当前仅 `app/src/test/` 下有 JVM 单元测试（JUnit 4）：`AppMonitorStatusTest`、`UsageStatsUseCaseTest`、`NotificationTimeTest`、`DayResetTimeTest`、`WidgetOpacityTest`、`OrgParserTest`、`OrgWriterTest`。`app/src/androidTest/` 源集不存在（`app/build.gradle.kts` 保留了 Espresso / Compose UI Test 依赖，但无设备测试）。

## 文档

- `docs/BUILD.md` — 构建指南（含 Guix 环境特殊说明）
- `docs/GUIX_ANDROID_BUILD.md` — Guix 系统上构建 Android 的详细文档
- `docs/CHANGELOG.md` — 版本变更日志
- `docs/CONTRIBUTING.md` — 改进循环、候选卡、门禁与提交规范
- `docs/DESIGN.md` — 视觉与交互设计原则（配色、明暗切换、小部件、设置页）
