# 019-GitHub CI自动打包前备份

## 备份信息

- 日期：2026-07-15
- 阶段：GitHub Actions 自动构建 Debug APK
- 目标文件：`.github/workflows/android-debug-apk.yml`
- 当前默认分支：`main`
- 当前版本：`1.4.0`

## 修改前状态

- 仓库中尚未存在 `.github/workflows/` 目录。
- `push` 到 GitHub 后不会自动触发任何构建、打包或发布流程。
- APK 仍需本地手动执行 Gradle 命令构建。
- 当前发布方式依赖手动上传到 GitHub Releases。

## 当前构建基线

- Android Gradle Plugin：`8.6.0`
- Kotlin：`1.9.25`
- Gradle Wrapper：`8.7`
- Java 目标版本：`17`
- `compileSdk`：`36`
- 当前 APK 输出文件名：`USB采集卡.apk`

## 计划中的最小改动

- 新增一个 GitHub Actions workflow。
- 触发条件限定为：
  - `push` 到 `main`
  - 手动触发 `workflow_dispatch`
- 工作流只做以下动作：
  - 检出代码
  - 配置 JDK 17
  - 配置 Android SDK
  - 生成 CI 使用的 `local.properties`
  - 执行 `:app:assembleDebug`
  - 上传生成的 Debug APK Artifact

## 本阶段边界

- 不改现有 Kotlin 业务逻辑。
- 不接入 GitHub Release 自动发布。
- 不接入 release keystore 或正式签名。
- 不改版本号策略。
