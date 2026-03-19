# 在 Guix 上编译 Android 项目完整教程

本文档面向两类场景：

- 你想在 Guix System 或安装了 Guix 的 GNU/Linux 上直接编译 Android 项目
- 你想在当前仓库上复现一套已经验证可工作的宿主机构建流程

本文档优先覆盖当前仓库 `dakala` 的实际需求，但大多数步骤也适用于其他 Android 项目。

## 1. 先理解 Guix 上为什么容易出问题

Android 构建链路里有一批官方预编译二进制，例如：

- `aapt2`
- `adb`
- `apkanalyzer`
- `zipalign`
- 模拟器相关二进制

这些程序通常假设系统是传统 FHS 布局，默认会去找：

```text
/lib64/ld-linux-x86-64.so.2
```

而 Guix/非 FHS 环境经常没有这个路径，于是你会遇到典型报错：

```text
AAPT2 ... Daemon startup failed
Exec failed, error: 2 (No such file or directory)
```

这个仓库已经内置了宿主机兼容层，所以你不需要再手工修补 `aapt2`。后文会说明怎么使用。

## 2. 当前仓库已经验证过的版本

当前项目要求：

- JDK 25
- Android SDK Platform 36.1
- Android SDK Build-Tools 36.1.0
- Android Platform-Tools
- Gradle Wrapper（使用仓库自带 `./gradlew`）

当前仓库中的关键配置可以在这些文件看到：

- [app/build.gradle.kts](./app/build.gradle.kts)
- [gradlew](./gradlew)
- [tools/aapt2/aapt2](./tools/aapt2/aapt2)

## 3. 准备 Guix 基础环境

先确认你在 Guix 上具备最基本的命令行工具。推荐直接进入一个临时开发环境：

```bash
guix shell \
  bash coreutils findutils grep sed gawk diffutils \
  git tar gzip unzip zip which \
  openjdk \
  --container --emulate-fhs
```

如果你不想开容器，也可以直接在宿主环境安装这些工具，核心要求只有两点：

- `java -version` 能正常输出
- `unzip`、`sed`、`grep`、`find`、`which` 这些基础工具可用

说明：

- `--emulate-fhs` 不是硬要求，但对 Android 生态很友好
- 当前仓库已经处理了最关键的 `aapt2` 问题，所以即使不启用 `--emulate-fhs`，也仍有机会直接成功

## 4. 安装并准备 JDK 25

先确认你拿到的是 JDK 25：

```bash
java -version
```

期望看到类似：

```text
openjdk version "25.x.x"
```

如果 `java` 已经在 `PATH` 里，通常不必额外设置 `JAVA_HOME`。  
这个仓库的 [gradlew](./gradlew) 优先使用 `JAVA_HOME`，否则回退到 `PATH` 中的 `java`。

如果你确实要手工指定：

```bash
export JAVA_HOME=/path/to/your/jdk25
export PATH="$JAVA_HOME/bin:$PATH"
```

再次确认：

```bash
java -version
```

## 5. 准备 Android SDK

### 5.1 推荐目录

推荐把 Android SDK 放在一个稳定路径，例如：

```bash
$HOME/Programs/Android/SDK
```

当前仓库就是按这个路径验证的。

### 5.2 需要安装哪些 SDK 组件

至少准备这些组件：

- `platform-tools`
- `build-tools;36.1.0`
- `platforms;android-36.1`
- `sources;android-36.1`（可选，但推荐）

如果你还要调试真机：

- `adb` 或 Android SDK 自带 `platform-tools`

如果你还要跑模拟器：

- `emulator`
- 对应的 system image

### 5.3 你可以怎么安装 SDK

常见有两种方式：

1. 用 Android Studio 的 SDK Manager 安装
2. 用 Google 的 command-line tools 里的 `sdkmanager` 安装

如果你用 `sdkmanager`，大致命令形式如下：

```bash
sdkmanager \
  "platform-tools" \
  "build-tools;36.1.0" \
  "platforms;android-36.1" \
  "sources;android-36.1"
```

然后接受许可证：

```bash
sdkmanager --licenses
```

如果你不使用 `sdkmanager`，只要 SDK Manager 已经把这些目录装好也可以。

### 5.4 如何确认 SDK 装好了

检查这些目录是否存在：

```bash
find "$HOME/Programs/Android/SDK" -maxdepth 2 -type d | sort
```

至少要能看到类似：

```text
.../build-tools/36.1.0
.../platform-tools
.../platforms/android-36.1
```

## 6. 让项目找到 Android SDK

有两种做法，任选一种即可。

### 方案 A：写 `local.properties`

在项目根目录创建或修改 `local.properties`：

```properties
sdk.dir=/home/yourname/Programs/Android/SDK
```

注意：

- 用绝对路径
- 路径里如果有空格，需要按 Java properties 规则转义

### 方案 B：用环境变量

```bash
export ANDROID_HOME="$HOME/Programs/Android/SDK"
export ANDROID_SDK_ROOT="$HOME/Programs/Android/SDK"
```

对于当前仓库，优先级大致可以理解为：

1. `ANDROID_SDK_ROOT`
2. `ANDROID_HOME`
3. `local.properties` 里的 `sdk.dir`

## 7. 当前仓库对 Guix 做了什么兼容

当前仓库已经添加了一个项目内的 `aapt2` 包装器：

- [tools/aapt2/aapt2](./tools/aapt2/aapt2)

同时 [gradlew](./gradlew) 会自动把它注入到 Gradle：

```text
android.aapt2FromMavenOverride=<project>/tools/aapt2/aapt2
```

这个包装器的工作方式是：

1. 优先查找 Android SDK 自带的 `build-tools/*/aapt2`
2. 如果能直接执行，就直接使用
3. 如果在 Guix/非 FHS 环境下直接执行失败，就自动通过宿主机的 glibc loader 启动它

所以对这个仓库来说，Guix 上通常不需要你自己 `patchelf`。

## 8. 直接构建当前项目

进入项目根目录：

```bash
cd /path/to/dakala
```

先构建 Debug：

```bash
./gradlew assembleDebug --no-configuration-cache
```

再构建 Release：

```bash
./gradlew assembleRelease --no-configuration-cache
```

成功后 APK 一般在：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

## 9. 如果你想在 Guix shell 中固定一套环境

最简单的做法是每次这样进入：

```bash
guix shell \
  bash coreutils findutils grep sed gawk diffutils \
  git tar gzip unzip zip which \
  openjdk \
  --container --emulate-fhs
```

进入后再设置 SDK：

```bash
export ANDROID_HOME="$HOME/Programs/Android/SDK"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
```

然后构建：

```bash
./gradlew assembleDebug --no-configuration-cache
```

如果你希望长期复用，建议把这套命令整理成：

- `manifest.scm`
- `guix shell -m manifest.scm`

不过这属于工程环境固化，不是当前仓库构建的必需条件。

## 10. 真机安装与调试

如果你用 Android SDK 自带的 `adb`：

```bash
$HOME/Programs/Android/SDK/platform-tools/adb devices
$HOME/Programs/Android/SDK/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

如果你用 Guix 里的 `adb`：

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 11. 最常见报错与处理方法

### 11.1 `AAPT2 ... Daemon startup failed`

先确认你已经使用的是当前仓库的最新版 [gradlew](./gradlew) 和 [tools/aapt2/aapt2](./tools/aapt2/aapt2)。

再确认 SDK 里确实有：

```text
build-tools/36.1.0/aapt2
```

检查命令：

```bash
ls -l "$ANDROID_HOME/build-tools/36.1.0/aapt2"
```

### 11.2 `SDK location not found`

说明项目没找到 Android SDK。检查下面三项：

- `local.properties` 是否存在且 `sdk.dir` 正确
- `ANDROID_HOME` 是否指向正确目录
- `ANDROID_SDK_ROOT` 是否指向正确目录

### 11.3 `JAVA_HOME is set to an invalid directory`

这说明你显式设置了错误的 `JAVA_HOME`。修正或取消：

```bash
unset JAVA_HOME
java -version
```

如果 `java` 本身不可用，再重新设置到 JDK 25。

### 11.4 Release 构建时签名失败

当前项目的 Release 构建依赖：

- `keystore.properties`
- `dakala.keystore`

如果你缺少自己的签名文件，Debug 构建通常不受影响。  
如果你只是想确认工程能编译，先跑：

```bash
./gradlew assembleDebug
```

### 11.5 `platforms/android-36.1` 不存在

这说明 SDK 组件没装完整。你需要补安装：

- Android SDK Platform 36.1
- Android SDK Build-Tools 36.1.0

## 12. 推荐的最小成功路径

如果你只想尽快把当前仓库在 Guix 上跑起来，按下面这套最短路径做即可：

1. 准备 JDK 25，并让 `java -version` 正常输出
2. 安装 Android SDK 到 `$HOME/Programs/Android/SDK`
3. 确保 SDK 中存在：
   - `build-tools/36.1.0`
   - `platform-tools`
   - `platforms/android-36.1`
4. 在项目根目录写好 `local.properties`
5. 直接执行：

```bash
./gradlew assembleDebug --no-configuration-cache
./gradlew assembleRelease --no-configuration-cache
```

## 13. 当前仓库的实用结论

对于这个仓库，在 Guix 上编译最关键的点不是 Kotlin、Gradle 或 Java 本身，而是 Android 预编译工具链的宿主机兼容性。这个问题现在已经通过项目内置的 `aapt2` 包装器处理掉了，所以你应当优先确保三件事：

- JDK 25 正常
- Android SDK 36.1 正常
- 一定使用仓库自带的 `./gradlew`

只要这三件事成立，当前仓库已经具备直接在 Guix 宿主机构建的条件。
