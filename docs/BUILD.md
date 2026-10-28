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

## 5. 发版与 CI

CI 配置在 `.github/workflows/`：

- **`ci.yml`**：push 到 main 与 PR 触发，跑 `ktlintCheck test assembleDebug` 门禁并上传 debug APK artifact。
- **`release.yml`**：推送 `v*` tag 触发，构建 APK 并创建 GitHub Release；release notes 自动取 `docs/CHANGELOG.md` 中 `## [x.y.z]` 段落的正文。

### 发版步骤

1. 在 `docs/CHANGELOG.md` 顶部把 `[Unreleased]`（或新增一节）写成 `## [x.y.z] - YYYY-MM-DD`，该段落即发布文案。
2. `app/build.gradle.kts` 中 `versionCode` 递增、`versionName` 改为 `x.y.z`。
3. 提交后打 **annotated tag**（`git tag -a`），然后**带 `--follow-tags` 推送**：

   ```bash
   git tag -a v1.0.7 -m "Release v1.0.7" && git push --follow-tags origin main
   ```

   两条都不可省：`git push` 默认不携带 tag，漏推则 `release.yml` 永不触发——main 上 CI 会跑且显示成功，但 Releases 页不会出现新版本（v1.2.0 曾踩此坑）；而 `--follow-tags` 只跟随 **annotated** tag，`git tag v1.0.7` 创建的 lightweight tag 即使带 `--follow-tags` 也不会被推上去（v1.2.1 首发时实测）。

### 签名 secrets（发布签名 APK 必须）

在 GitHub 仓库 Settings → Secrets and variables → Actions 配置：

| Secret | 内容 |
|--------|------|
| `KEYSTORE_BASE64` | `base64 -w0 dakala.keystore` 的输出 |
| `STORE_PASSWORD` | keystore.properties 的 `storePassword` |
| `KEY_ALIAS` | keystore.properties 的 `keyAlias` |
| `KEY_PASSWORD` | keystore.properties 的 `keyPassword` |

未配置时 release workflow 降级为发布 debug 签名 APK（可安装，但不是正式发布签名）。
