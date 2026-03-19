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

- 🤖 **代码生成**：基于 **GLM-5 (负责初版)** 、 **Claude Sonnet 4.5 (新增功能)** 和 **gpt-5.4 (修复构建)** 等模型生成核心逻辑。
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
- **构建环境**：支持直接在 Linux 宿主机构建；如需隔离环境，也可以继续使用 `distrobox` 容器

## 📚 文档索引

- `README.md`：项目简介、常规开发与构建说明
- [GUIX_ANDROID_BUILD.md](./GUIX_ANDROID_BUILD.md)：在 Guix 上从零准备 Android 构建环境的完整教程
- [HOST_BUILD.log](./HOST_BUILD.log)：本项目宿主机构建兼容修复记录
- [CHANGELOG.md](./CHANGELOG.md)：版本更新日志

## 📦 编译指南

### 1. 准备 JDK 与 Android SDK

先确认 Java 25 可用：

```bash
java -version
```

然后确认 Android SDK 已安装，并且包含至少这些组件：

- `platform-tools`
- `build-tools/36.1.0`
- `platforms/android-36.1`

项目可以通过两种方式找到 SDK：

- 根目录 `local.properties` 中的 `sdk.dir`
- 环境变量 `ANDROID_HOME` / `ANDROID_SDK_ROOT`

#### 直接在宿主机构建

```bash
# 推荐先检查 SDK 配置
cat local.properties

# 或者检查环境变量
printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "$ANDROID_HOME" "$ANDROID_SDK_ROOT"
```

说明：

- `./gradlew` 现在会自动为宿主机构建启用项目内的 `aapt2` 兼容包装器，处理非 FHS Linux 上的动态链接问题
- 只要本机有 JDK 25 和可用的 Android SDK，就可以直接在项目目录构建
- Guix 用户建议直接阅读 [GUIX_ANDROID_BUILD.md](./GUIX_ANDROID_BUILD.md)

#### 继续使用容器（可选）

```bash
# 进入容器
distrobox enter archlinux

# 安装 JDK 25
sudo pacman -S jdk25-temurin
```

### 2. 准备项目目录

```bash
cd /path/to/your/workspace
# 如果项目已在本地，跳过此步骤
```

### 3. 编译项目

```bash
# Debug 构建
./gradlew assembleDebug --no-configuration-cache

# Release 构建
./gradlew assembleRelease --no-configuration-cache
```

说明：

- 请使用项目自带的 `./gradlew`，不要直接使用系统 `gradle`
- `gradlew` 已处理容器中的 `JAVA_HOME`/证书路径问题，以及宿主机上的 `aapt2` 动态链接兼容问题
- 当前构建链路已验证可在宿主机直接使用 JDK 25 编译，也可继续在 `archlinux` 容器内构建

如果你只想确认工程能编译，优先跑 `assembleDebug`。  
`assembleRelease` 还会依赖 release 签名配置。

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
./gradlew test

# 运行Android测试（需要连接设备）
./gradlew connectedAndroidTest
```

### 代码检查

```bash
# Lint检查
./gradlew lint

# Kotlin代码风格检查
./gradlew ktlintCheck
```

### 清理项目

```bash
./gradlew clean
```

---

## 📄 许可证

MIT License
