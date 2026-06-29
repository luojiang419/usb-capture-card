# 014-GitHub发布与README

## 已完成内容
- 已读取最新快照 `013-等待状态UI精简.md`，从上一阶段继续。
- 已创建修改前备份：`D:\Myproject\usb摄像头\backup\013-GitHub发布与README前备份.md`。
- 新增 `.gitignore`：
  - 忽略 `.gradle/`、`build/`、`app/build/`。
  - 忽略 `local.properties`。
  - 忽略本地 APK 输出，APK 通过 GitHub Releases 发布，不提交到 Git。
- 新增详细 `README.md`，包含：
  - 项目简介。
  - 主要功能。
  - 使用方式。
  - 技术栈。
  - 项目结构。
  - 本地构建方式。
  - 权限说明。
  - 当前版本能力。
  - 已知限制。
  - 发布流程。
- 初始化 Git 仓库，分支为 `main`。
- 完成初始提交：`fd010b6 Initial USB capture card app`。
- 创建 GitHub 私有仓库：
  - 仓库地址：`https://github.com/luojiang419/usb-capture-card`
  - 可见性：`PRIVATE`
  - 默认分支：`main`
- 已推送当前项目源码和文档到 GitHub。
- 创建 GitHub Release：
  - Release：`v1.4.0`
  - 地址：`https://github.com/luojiang419/usb-capture-card/releases/tag/v1.4.0`
  - 标题：`USB采集卡 v1.4.0`
- 已上传 APK 发布资产：
  - 资产名：`USB-Capture-Card-v1.4.0.apk`
  - 大小：`26190876` bytes
  - SHA-256：`91a6830fa55d6a9a5140486d4a05e7459e8910c3dfb35a555528863a1da753f0`
  - 下载地址：`https://github.com/luojiang419/usb-capture-card/releases/download/v1.4.0/USB-Capture-Card-v1.4.0.apk`
- 发现 GitHub 会将中文 APK 文件名规范化为 `USB.apk`，因此已改用稳定英文发布资产名。
- 本地构建产物仍保留为：`D:\Myproject\usb摄像头\app\build\outputs\apk\debug\USB采集卡.apk`

## 当前修改到哪个模块
模块十三：GitHub 仓库发布与 README 文档。

## 具体修改的代码前后对比

### 1. 新增 `.gitignore`

修改前：
```text
// 无 .gitignore，Git 初始化后会误收录构建目录、本机 local.properties 或 APK 输出。
```

修改后：
```gitignore
.gradle/
build/
app/build/
.idea/
*.iml
local.properties
*.apk
```

### 2. 新增 README 文档

修改前：
```text
// 无 README.md。
```

修改后：
```markdown
# USB采集卡

一个面向 Android 设备的 USB/UVC 采集卡预览与录制应用。
```

README 已补充功能、安装、构建、权限、版本能力和发布说明。

### 3. GitHub Release 资产名修正

修改前：
```text
本地 APK 文件名：USB采集卡.apk
GitHub 初次上传后资产名被规范化为：USB.apk
```

修改后：
```text
GitHub Release 资产名：USB-Capture-Card-v1.4.0.apk
本地构建产物仍为：USB采集卡.apk
```

## 待办清单（未完成）
- 如果后续要公开仓库，需要补充正式 LICENSE 文件。
- 如果后续要正式分发，需要配置 Release 签名 APK，而不是 Debug APK。
- 真机下载 Release APK 并安装验证。

## 下一步要做什么
在 GitHub Release 页面下载 `USB-Capture-Card-v1.4.0.apk`，安装到真机，连接 USB/UVC 采集卡完成预览和录制链路验证。
