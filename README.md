# USB采集卡

一个面向 Android 设备的 USB/UVC 采集卡预览与录制应用。项目使用 Kotlin、Jetpack Compose 和 AUSBC 3.2.7 构建，目标是让手机或平板连接 USB 采集卡后，可以快速查看 HDMI/采集画面、切换采集参数并录制视频。

当前版本：`1.4.2`  
应用包名：`com.codex.usbcapture`  
最低系统版本：Android 6.0，API 23  
发布 APK：见 GitHub Releases 中最新标签对应的 `USB-Capture-Card-v版本号.apk`

## 主要功能

- USB/UVC 采集卡自动识别：支持 Android USB Host，插入采集卡后自动触发设备检测和权限请求。
- 实时预览：基于 AUSBC 的 `CameraClient + CameraUvcStrategy` 接入 UVC 视频流。
- 沉浸式采集画面：预览开始后隐藏控制层，点击画面可唤出左上角功能按钮。
- 左上角功能按钮：集中入口用于打开录制、采集设置和退出采集画面。
- 状态指示灯：功能按钮内显示采集卡状态，等待、连接中、已连接和错误状态有不同颜色/动效。
- 等待页优化：未连接时屏幕中心只显示 USB 图标和“等待采集卡”，减少重复提示。
- 锁屏防误触：预览时可通过右侧锁定按钮锁定画面，锁定后点击画面不会唤出功能菜单。
- 视频录制：支持开始/停止录制，录制文件保存到应用专属目录。
- 录制文件管理：应用内读取已录制 MP4 文件列表，方便后续查看和分享入口扩展。
- 分辨率选择：提供当前采集库返回的分辨率列表，并支持自动检测可用分辨率。
- 帧速率预设：提供 24 / 30 / 60 帧每秒选项。
- 实时采集帧率显示：预览时右上角显示按实际帧回调统计的采集 FPS。
- 横竖屏自适应：支持系统自动旋转，横竖屏变化后刷新渲染尺寸。
- 中文界面：状态、错误提示、按钮和录制文件名均使用中文表达。
- 深浅色主题：支持跟随系统、浅色、暗黑三种主题模式。
- 应用内更新：启动后会检查 GitHub 最新 Release，发现新版本后可直接下载 APK 并提示安装。

## 使用方式

1. 在 GitHub Releases 下载最新标签对应的 `USB-Capture-Card-v版本号.apk`。
2. 将 APK 安装到支持 USB Host 的 Android 手机或平板。
3. 使用 OTG 转接线连接 USB/HDMI 采集卡。
4. 首次连接时授予 USB 设备权限、相机权限和录音权限。
5. 采集卡输出视频信号后，应用会进入实时预览。
6. 点击预览画面左上角功能按钮，可进行录制、设置分辨率/帧速率或退出采集画面。
7. 预览时可点击右侧锁定按钮防止误触。

录制文件默认保存到应用专属目录：

```text
Android/data/com.codex.usbcapture/files/Movies/UsbCapture
```

文件名前缀为：

```text
采集录像_yyyyMMdd_HHmmss.mp4
```

## 技术栈

- Kotlin
- Android Gradle Plugin
- Jetpack Compose
- Material 3
- AndroidX AppCompat / Core / Lifecycle
- AUSBC 3.2.7
- libuvc 3.2.7

核心依赖：

```kotlin
implementation("com.github.jiangdongguo.AndroidUSBCamera:libausbc:3.2.7")
implementation("com.github.jiangdongguo.AndroidUSBCamera:libuvc:3.2.7")
implementation("androidx.compose.material3:material3:1.3.1")
```

## 项目结构

```text
.
├── app
│   ├── build.gradle.kts
│   └── src/main
│       ├── AndroidManifest.xml
│       ├── java/com/codex/usbcapture/MainActivity.kt
│       └── res
│           ├── drawable/ic_launcher.xml
│           ├── values
│           └── xml
│               ├── device_filter.xml
│               └── file_paths.xml
├── gradle
├── backup
├── 进度快照
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle.kts
└── README.md
```

说明：

- `MainActivity.kt`：主要应用逻辑和 Compose UI，包括 USB 状态、预览控制、录制、设置面板和沉浸式交互。
- `device_filter.xml`：USB 设备过滤配置。
- `file_paths.xml`：录制文件分享路径配置。
- `backup/`：每个功能阶段开始前的修改前备份说明。
- `进度快照/`：每个功能模块完成后的进度记录和代码前后对比。

## 本地构建

### 环境要求

- Windows / macOS / Linux 均可构建 Android 项目。
- JDK 17。
- Android SDK，项目当前 `compileSdk = 36`、`targetSdk = 36`。
- 如果在本机使用 Flutter/Android 环境，确保 `local.properties` 中的 `sdk.dir` 指向本机 Android SDK。

本项目不提交 `local.properties`，因为它是本机路径配置。克隆后请按自己的 SDK 路径创建：

```properties
sdk.dir=D\:\\Android\\Sdk
```

### 编译 Debug APK

Windows：

```powershell
.\gradlew.bat :app:assembleDebug
```

macOS / Linux：

```bash
./gradlew :app:assembleDebug
```

构建产物：

```text
app/build/outputs/apk/debug/USB采集卡.apk
```

说明：本地构建产物使用中文文件名 `USB采集卡.apk`；GitHub Release 中使用更稳定的英文资产名 `USB-Capture-Card-v版本号.apk`。

## 权限说明

应用会请求以下权限：

- `CAMERA`：AUSBC/UVC 预览链路需要。
- `INTERNET`：检查 GitHub 最新 Release 并下载更新包。
- `RECORD_AUDIO`：录制采集视频时使用音频能力。
- `REQUEST_INSTALL_PACKAGES`：允许应用拉起系统安装器安装新版 APK。
- `WAKE_LOCK`：采集和录制时保持设备唤醒。
- USB 设备权限：Android 系统在插入采集卡后弹窗授权。

## 当前版本能力

`1.4.2` 版本包含以下重点优化：

- 左上角只保留功能按钮，不再重复显示“等待采集卡”状态胶囊。
- 功能按钮内增加状态指示灯。
- 未连接时中心等待页只显示 USB 图标和“等待采集卡”。
- 等待状态下隐藏默认分辨率副标题，避免出现“采集卡已连接 1280 × 720”这类误导信息。
- 新增 GitHub Release 应用内更新能力：启动后自动检查新版本，可下载 APK 并提示安装。

## 已知限制

- 当前帧速率选项是应用侧预设。AUSBC 3.2.7 未直接暴露所有采集卡真实 FPS 枚举，因此不同采集卡实际支持情况可能不同。
- 分辨率列表依赖采集库返回的数据。部分采集卡可能只返回有限分辨率。
- `1.4.1` 及更早版本还不包含这套应用内更新逻辑，因此首次切入自动更新能力时，仍需要手动安装一次 `1.4.2` 或更高版本。
- 需要真机和真实 USB/UVC 采集卡验证完整链路，普通模拟器无法验证 USB 采集功能。
- 当前发布的是 Debug APK，适合测试和内部使用；正式分发前建议配置 Release 签名、混淆和版本发布流程。

## 发布流程

当前项目已接入 GitHub Actions 自动发布：

1. 日常开发时，`push` 到 `main` 会自动构建 Debug APK，并上传到 Actions Artifacts。
2. 需要正式挂到 GitHub Releases 时，先更新版本号并推送对应代码到 `main`。
3. 创建版本标签，例如 `v1.4.1`：

```bash
git tag v1.4.1
git push origin v1.4.1
```

4. GitHub Actions 会自动：
   - 构建 `app/build/outputs/apk/debug/USB采集卡.apk`
   - 复制为 Release 资产名 `USB-Capture-Card-v1.4.1.apk`
   - 创建或更新对应标签的 GitHub Release
5. 发布完成后，可在 Releases 页面直接下载 APK。

## 许可证

当前仓库尚未添加开源许可证。公开分发或开放协作前，请根据实际使用目标补充合适的 LICENSE 文件。
