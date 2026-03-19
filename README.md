<div align="center">
  <img width="200" alt="ic_launcher" src="https://github.com/user-attachments/assets/8ea81075-0bc4-4faf-b83c-d86c5d640bc6" />
  
  # 📅 打卡啦 (Dakala)
  
  **一款帮助用户日常打卡的 Android 应用**
</div>

## 📖 项目简介

你是否经常因为忙碌而错过游戏的每日签到奖励？

**打卡啦 (Dakala)** 专为解决这一痛点而生。它的核心原理是**监测应用使用时长**：如果您在当天尚未打开目标应用（或打开时长未达标），它会在您设定的时间发出智能提醒，确保您不再错过任何每日奖励。

## 📱 项目截图

<table>
  <tr>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/bacacee8-29e4-4e0a-9c1a-ac538e53ece5" alt="截图1" width="200"/>
      <br/>
      <sub>主界面</sub>
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/e636bbe6-1d2b-4f9a-8c4e-2b4e66979f87" alt="截图2" width="200"/>
      <br/>
      <sub>主页面 (夜间模式)</sub>
    </td>
  </tr>
</table>

---

## 🛠️ 技术栈与开发流程

本项目采用了高效的 AI 辅助开发流程：

- 🤖 **代码生成**：基于 **GLM-5** 和 **Qwen-Coder** 大模型生成核心逻辑。
- ✍️ **编辑与验收**：使用 **Zed** 编辑器进行代码审查、微调及最终成果验收。

---

## 📱 功能特点

- **应用监控**：从系统应用列表中选择要监控的应用（如微信、抖音）
- **状态展示**：主界面显示已选应用列表、今日是否打开、今日打开时长
- **打卡提醒**：设置时间点（如22:00），自动发送通知提示未完成的应用
- **桌面小部件**：快速查看未打开/时长不足的应用，点击可快速打开

## 🏗️ 项目架构

本项目采用 **MVVM + Jetpack** 架构，遵循Android官方推荐的最佳实践。

```
app/src/main/java/com/dakala/app/
├── data/                    # 数据层
│   ├── local/              # 本地数据存储
│   │   ├── dao/            # Room DAO接口
│   │   ├── database/       # 数据库配置
│   │   └── entity/         # 数据实体类
│   └── repository/         # 数据仓库
├── di/                     # 依赖注入模块（Hilt）
├── domain/                 # 领域层
│   ├── model/              # 业务模型
│   └── usecase/            # 用例类
├── receiver/               # 广播接收器
├── ui/                     # UI层
│   ├── components/         # Compose组件
│   ├── theme/              # 主题配置
│   ├── util/               # UI工具类
│   └── viewmodel/          # ViewModel
├── widget/                 # 桌面小部件
└── worker/                 # 后台任务（WorkManager）
```

### 技术栈

| 类别     | 技术                         |
| -------- | ---------------------------- |
| 语言     | Kotlin 2.3.10                |
| UI       | Jetpack Compose + Material 3 |
| 架构     | MVVM + Clean Architecture    |
| 依赖注入 | Hilt                         |
| 数据库   | Room                         |
| 后台任务 | WorkManager                  |
| 应用统计 | UsageStatsManager            |

## 🔧 环境要求

- **操作系统**：Linux（推荐 Ubuntu 22.04+ 或 Arch Linux）
- **JDK**：OpenJDK 25
- **Android SDK**：API 29 (Android 10) ~ API 36.1 (Android 16)
- **Android Studio**：Ladybug (2024.2.1) 或更高版本（可选，用于调试）
- **构建环境**：需要在 Linux 容器内执行，当前项目使用 `distrobox` 管理的 `archlinux` 容器

## 📦 编译指南

### 1. 准备容器环境

#### Arch Linux

```bash
# 进入构建容器
distrobox enter archlinux

# 安装 JDK 25 与 Android 构建常用工具
sudo pacman -S jdk25-temurin
```

### 2. 克隆项目

```bash
cd /path/to/your/workspace
# 如果项目已在本地，跳过此步骤
```

### 3. 编译项目

```bash
# 在宿主机项目目录执行，命令会进入 archlinux 容器后再构建
distrobox enter archlinux -- bash -lc 'cd /run/host$PWD && ./gradlew assembleDebug'

# Release 构建
distrobox enter archlinux -- bash -lc 'cd /run/host$PWD && ./gradlew assembleRelease'
```

说明：

- 请使用项目自带的 `./gradlew`，不要直接使用系统 `gradle`
- `gradlew` 已处理容器中常见的 `JAVA_HOME` 与证书路径透传问题
- 当前构建链路已验证可在 `archlinux` 容器内使用 JDK 25 编译通过

编译完成后，APK文件位于：

- Debug版本：`app/build/outputs/apk/debug/app-debug.apk`
- Release版本：`app/build/outputs/apk/release/app-release.apk`

### 4. 安装到设备

#### 使用ADB安装

```bash
# 确保设备已连接并开启USB调试
adb devices

# 安装APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

#### 使用Android Studio

1. 打开Android Studio
2. 选择 `File > Open`，选择项目目录
3. 等待Gradle同步完成
4. 连接Android设备或启动模拟器
5. 点击 `Run > Run 'app'` 或按 `Shift+F10`

## 🚀 运行指南

### 首次运行

1. **安装应用**：按照上述步骤安装APK

2. **授权使用统计权限**（重要）：
   - 打开应用后，会提示需要授权
   - 前往：**设置 > 应用 > 打卡啦 > 权限 > 最近使用的应用**
   - 开启权限开关

3. **选择监控应用**：
   - 点击主界面右下角的"设置监控应用"按钮
   - 勾选需要监控的应用
   - 点击"保存"

4. **设置通知时间**（可选）：
   - 点击右上角的通知图标
   - 选择提醒时间（默认22:00）

### 使用桌面小部件

1. 长按桌面空白处
2. 选择"小部件"或"Widgets"
3. 找到"打卡状态"小部件
4. 拖动到桌面合适位置

## ⚠️ 重要说明

### 权限处理

本应用需要 `PACKAGE_USAGE_STATS` 权限来获取应用使用统计数据。这是一个特殊权限，**无法通过代码直接请求**，必须引导用户到系统设置中手动开启。

权限路径：

```
设置 > 应用 > 打卡啦 > 权限 > 最近使用的应用 > 开启
```

### 兼容性

- **最低支持**：Android 10 (API 29)
- **目标版本**：Android 16 (API 36.1)
- **最高测试**：Android 16 (API 36.1)
- **推荐设备**：Android 12 及以上

### 隐私声明

- 本应用**不收集任何用户数据**
- 所有数据存储在本地设备
- 不使用网络权限，不上传任何信息
- 不使用AccessibilityService（Google Play禁止）

## 📖 代码结构说明

### 数据层 (Data Layer)

| 文件                    | 说明                       |
| ----------------------- | -------------------------- |
| `AppItem.kt`            | 应用监控项实体类           |
| `UsageRecord.kt`        | 使用记录实体类             |
| `AppUsageDatabase.kt`   | Room数据库配置             |
| `AppDao.kt`             | 数据访问对象接口           |
| `AppUsageRepository.kt` | 数据仓库，封装数据访问逻辑 |

### 业务逻辑层 (Domain Layer)

| 文件                   | 说明                      |
| ---------------------- | ------------------------- |
| `UsageStatsUseCase.kt` | 封装UsageStatsManager调用 |
| `AppMonitorStatus.kt`  | 应用监控状态UI模型        |

### UI层 (UI Layer)

| 文件                      | 说明                 |
| ------------------------- | -------------------- |
| `MainActivity.kt`         | 主界面，显示监控状态 |
| `AppSelectionActivity.kt` | 应用选择界面         |
| `MainViewModel.kt`        | 主界面ViewModel      |
| `PermissionHelper.kt`     | 权限检查和引导工具   |

### 后台服务 (Background Services)

| 文件                     | 说明           |
| ------------------------ | -------------- |
| `NotificationWorker.kt`  | 定时通知任务   |
| `UsageWidgetProvider.kt` | 桌面小部件     |
| `BootReceiver.kt`        | 开机启动接收器 |

## 🛠️ 开发指南

### 运行测试

```bash
# 运行单元测试
gradle test

# 运行Android测试（需要连接设备）
gradle connectedAndroidTest
```

### 代码检查

```bash
# Lint检查
gradle lint

# Kotlin代码风格检查
gradle ktlintCheck
```

### 清理项目

```bash
gradle clean
```

## 📝 更新日志

### v1.1.0 - 自定义打卡功能 (开发中)

新增"自定义打卡"功能，支持用户创建自定义打卡项，可使用emoji或图片作为图标。

#### 功能概述

- **Tab切换**：主界面和小部件支持"应用打卡"和"自定义打卡"两个Tab切换
- **自定义打卡项**：支持创建、编辑、删除自定义打卡项
- **图标支持**：支持emoji和图片作为打卡项图标，图片自动应用圆角效果
- **打卡记录**：记录每日打卡状态和完成时间

#### 代码改动清单

##### 数据层 (Data Layer)

| 文件 | 改动说明 |
| --- | --- |
| `data/local/entity/AppItem.kt` | 新增 `CustomCheckItem` 实体（id, name, iconType, iconData）和 `CustomCheckRecord` 实体（itemId, date, isCompleted, completedAt） |
| `data/local/dao/AppDao.kt` | 新增 `CustomCheckItemDao` 和 `CustomCheckRecordDao` 接口，提供CRUD操作 |
| `data/local/database/AppUsageDatabase.kt` | 数据库版本从1升级到3，添加新实体，使用 `fallbackToDestructiveMigration` |
| `data/repository/AppUsageRepository.kt` | 添加自定义打卡相关的Repository方法 |

##### Domain层

| 文件 | 改动说明 |
| --- | --- |
| `domain/model/AppMonitorStatus.kt` | 新增 `CustomCheckStatus` 和 `CustomCheckStatusGroup` UI模型 |

##### 依赖注入 (DI)

| 文件 | 改动说明 |
| --- | --- |
| `di/AppModule.kt` | 添加 `CustomCheckItemDao` 和 `CustomCheckRecordDao` 的依赖注入 |

##### UI层

| 文件 | 改动说明 |
| --- | --- |
| `ui/MainActivity.kt` | 添加Tab切换逻辑，支持"应用打卡"和"自定义打卡"两个页面 |
| `ui/components/CustomCheckComponents.kt` | **新建** - 自定义打卡UI组件，包括打卡项列表、添加/编辑对话框、emoji输入和图片选择功能 |
| `ui/viewmodel/MainViewModel.kt` | 添加自定义打卡相关的StateFlow和方法 |

##### 小部件 (Widget)

| 文件 | 改动说明 |
| --- | --- |
| `widget/UsageWidgetProvider.kt` | 添加Tab切换逻辑和自定义打卡列表显示支持 |
| `widget/WidgetService.kt` | 新增 `CustomCheckRemoteViewsFactory` 用于自定义打卡列表渲染 |

##### 资源文件

| 文件 | 改动说明 |
| --- | --- |
| `res/layout/widget_usage.xml` | 添加Tab按钮（应用打卡/自定义打卡） |
| `res/layout/widget_custom_check_item.xml` | **新建** - 自定义打卡项布局 |
| `res/drawable/ic_check_outline.xml` | **新建** - 未完成状态图标（空心圆） |
| `res/drawable/ic_check_filled.xml` | **新建** - 已完成状态图标（实心圆带勾） |
| `res/drawable/tab_background.xml` | **新建** - Tab按钮背景选择器 |
| `res/values/strings.xml` | 添加新字符串资源 |

#### 数据库变更

```kotlin
// 新增表结构
@Entity(tableName = "custom_check_items")
data class CustomCheckItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconType: IconType,  // EMOJI or IMAGE
    val iconData: String     // emoji字符或图片URI
)

@Entity(
    tableName = "custom_check_records",
    primaryKeys = ["itemId", "date"]
)
data class CustomCheckRecord(
    val itemId: Long,
    val date: LocalDate,
    val isCompleted: Boolean,
    val completedAt: LocalDateTime?
)
```

#### 已修复问题

- [x] 小部件载入自定义打卡列表问题 - 已修复，添加了 `notifyAppWidgetViewDataChanged()` 调用以确保 ListView 正确刷新数据
- [x] MIUI小部件兼容性问题 - 已修复，移除了 `setSelected()` 方法调用（MIUI的自定义TextView不支持此方法在RemoteViews中使用）

---

## 📄 许可证

MIT License
