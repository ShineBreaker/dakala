# 打卡啦 (Dakala)

一款帮助用户监控已选应用每日使用情况的Android应用。

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
| 语言     | Kotlin 2.1.0                 |
| UI       | Jetpack Compose + Material 3 |
| 架构     | MVVM + Clean Architecture    |
| 依赖注入 | Hilt                         |
| 数据库   | Room                         |
| 后台任务 | WorkManager                  |
| 应用统计 | UsageStatsManager            |

## 🔧 环境要求

- **操作系统**：Linux（推荐 Ubuntu 22.04+ 或 Arch Linux）
- **JDK**：OpenJDK 17+
- **Android SDK**：API 29 (Android 10) ~ API 35 (Android 15)
- **Android Studio**：Ladybug (2024.2.1) 或更高版本（可选，用于调试）

## 📦 编译指南

### 1. 安装依赖

#### Arch Linux

```bash
# 安装JDK 17
sudo pacman -S jdk17-openjdk

# 设置JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
```

#### Ubuntu/Debian

```bash
# 安装JDK 17
sudo apt update
sudo apt install openjdk-17-jdk

# 设置JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
```

### 2. 克隆项目

```bash
cd /path/to/your/workspace
# 如果项目已在本地，跳过此步骤
```

### 3. 编译项目

```bash
cd dakala

# 赋予Gradle Wrapper执行权限
chmod +x gradlew

# 编译Debug版本
gradle assembleDebug

# 编译Release版本
gradle assembleRelease
```

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
- **最高测试**：Android 15 (API 35)
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

## 📄 许可证

MIT License
