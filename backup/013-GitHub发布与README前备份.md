# 013-GitHub发布与README前备份

## 备份时间
2026-06-29 创建 GitHub 仓库、发布 APK、补充 README 前。

## 当前阶段
模块十三：GitHub 仓库发布与项目文档。

## 修改目标
- 初始化 Git 仓库并推送当前项目到 GitHub。
- 将当前 APK 作为 GitHub Release 资产发布。
- 新增详细 README 文档，介绍当前项目功能、安装方式、构建方式和注意事项。
- 新增 `.gitignore`，避免提交本机构建缓存、APK 构建目录和本机 SDK 路径配置。

## 修改前状态
- 当前目录不是 Git 仓库。
- GitHub CLI 已登录账号：`luojiang419`。
- 目标仓库 `luojiang419/usb-capture-card` 尚不存在。
- 当前 APK：`D:\Myproject\usb摄像头\app\build\outputs\apk\debug\USB采集卡.apk`
- 当前版本：
  - `versionCode = 5`
  - `versionName = "1.4.0"`
- 当前没有 `README.md`。
- 当前没有 `.gitignore`。

## 发布前关键文件
```text
D:\Myproject\usb摄像头
├── app
├── backup
├── gradle
├── 进度快照
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── local.properties
└── settings.gradle.kts
```

## 本阶段计划
- 新增 `.gitignore`。
- 新增 `README.md`。
- 重新确认 APK 构建产物和元数据。
- 初始化 Git 仓库、提交源码和文档。
- 创建 GitHub 私有仓库并推送。
- 创建 `v1.4.0` Release，上传 `USB采集卡.apk`。
- 生成 `014-GitHub发布与README.md` 进度快照。
