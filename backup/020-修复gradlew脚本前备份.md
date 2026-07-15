# 020-修复gradlew脚本前备份

## 备份信息

- 日期：2026-07-15
- 阶段：修复 GitHub Actions 中的 `gradlew` 启动失败
- 目标文件：`gradlew`
- 关联工作流：`.github/workflows/android-debug-apk.yml`

## 修改前现象

- GitHub Actions 在 Ubuntu runner 上执行：

```bash
./gradlew --no-daemon :app:assembleDebug
```

- `Build debug APK` 步骤立即失败，日志只显示：

```text
Process completed with exit code 2.
```

- 前置步骤 `Set up JDK 17`、`Set up Android SDK`、`Install Android platform 36`、`Prepare local.properties for CI` 全部成功，因此失败点不在 Android SDK 配置。

## 当前异常状态

- 当前仓库中的 `gradlew` 文件长度仅 `385` bytes，明显短于标准 Gradle Wrapper 脚本。
- 当前脚本内容中存在以下路径计算：

```sh
APP_BASE_NAME=${0##*/}
APP_HOME=$( cd "${APP_BASE_NAME%/*}" >/dev/null 2>&1 && pwd -P ) || exit
```

- 当脚本以 `./gradlew` 运行时，`APP_BASE_NAME` 会变成 `gradlew`，随后尝试 `cd gradlew`，导致脚本在真正启动 Java/Gradle 前就提前退出。

## 预期修复

- 使用 `gradlew.bat wrapper` 重新生成标准 `gradlew` 脚本，而不是手工拼接脚本内容。
- 保持现有 `gradle-wrapper.properties` 中的 Gradle 版本配置不变。
- 修复后验证：
  - `bash ./gradlew --version`
  - `.\gradlew.bat :app:assembleDebug --offline`

## 本阶段边界

- 不改 Kotlin 业务逻辑。
- 不改 GitHub Actions 触发策略。
- 不改 Release 发布流程。
