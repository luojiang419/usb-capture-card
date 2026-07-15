# 021-修复gradlew脚本

## 已完成内容

- 读取最新快照 `020-GitHub CI自动打包.md`，继续处理 GitHub Actions 中 `Build debug APK` 失败问题。
- 创建阶段备份 `backup/020-修复gradlew脚本前备份.md`。
- 定位到失败根因不在 Android SDK、JDK 或 `local.properties`，而在仓库中的 Unix Wrapper 脚本 `gradlew`。
- 使用 `.\gradlew.bat wrapper` 重新生成标准 Gradle Wrapper 文件，而不是手工拼接脚本。
- 重建后以下文件已恢复为标准 Gradle Wrapper 产物：
  - `gradlew`
  - `gradlew.bat`
  - `gradle/wrapper/gradle-wrapper.jar`

## 当前修改到哪个模块

模块二十：修复 GitHub Actions 中的 `gradlew` 启动失败。Wrapper 已重建，等待推送后远端 CI 回归验证。

## 具体修改的代码前后对比

### 1. 修改前 `gradlew` 是异常的截断脚本

修改前文件长度仅 `385` bytes，核心路径计算如下：

```sh
APP_BASE_NAME=${0##*/}
APP_HOME=$( cd "${APP_BASE_NAME%/*}" >/dev/null 2>&1 && pwd -P ) || exit
```

当以 `./gradlew` 启动时，脚本会尝试 `cd gradlew`，在真正执行 Java 前就退出。

### 2. 修改后 `gradlew` 恢复为标准 POSIX Wrapper

修改后文件长度变为 `8692` bytes，头部恢复为标准 Gradle 生成脚本：

```sh
#!/bin/sh

# Copyright © 2015-2021 the original authors.
#
# Licensed under the Apache License, Version 2.0
```

### 3. Windows Wrapper 也同步恢复为标准版本

修改后 `gradlew.bat` 重新包含标准变量初始化和 `APP_HOME` 解析逻辑：

```bat
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%
```

## 验收标准

- `gradlew` 不再是截断脚本，能作为标准 Unix Wrapper 提交到仓库。
- `.\gradlew.bat :app:assembleDebug --offline` 本地继续成功。
- 推送后 GitHub Actions 中的 `./gradlew --no-daemon :app:assembleDebug` 不再秒退。
- 远端 `Upload debug APK artifact` 步骤能够被执行。

## 验证结果

- `.\gradlew.bat wrapper`：`BUILD SUCCESSFUL`。
- `.\gradlew.bat :app:assembleDebug --offline`：`BUILD SUCCESSFUL`。
- `git diff --check`：通过，仅有 `gradlew` 的 LF/CRLF 提示。
- 重建后：
  - `gradlew` 长度：`8692` bytes
  - `gradlew` SHA-256：`FC977A94723AF68AAFFA4E5D60496FB4AEED1884B6B19E5E2F2FD7612673313D`
- 当前本机 `bash ./gradlew --version` 验证在 Windows 环境下超时，未能作为最终结论使用。
- 但 `gradlew` 已恢复为 Gradle 自动生成的标准脚本，因此是否彻底修复仍以 GitHub Ubuntu runner 的下一次真实执行为准。

## 待办清单（未完成）

- 推送本次 Wrapper 修复到 GitHub。
- 观察 `Android Debug APK` workflow 是否通过。
- 若通过，下载 Artifact 确认 APK 产物存在。
- 若仍失败，再根据新的远端日志继续缩小范围。

## 下一步要做什么

提交并推送本次 `gradlew` 修复，等待 GitHub Actions 自动重跑或手动重跑。重点观察 `Build debug APK` 步骤是否从“秒退 exit code 2”变成正常的 Gradle 构建输出。
