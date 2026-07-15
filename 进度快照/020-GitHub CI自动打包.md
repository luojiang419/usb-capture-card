# 020-GitHub CI自动打包

## 已完成内容

- 读取最新快照 `019-放大后单指浏览.md`，在不改业务代码的前提下开始接入 GitHub Actions。
- 创建阶段备份 `backup/019-GitHub CI自动打包前备份.md`。
- 新增工作流文件 `.github/workflows/android-debug-apk.yml`。
- 工作流已配置为：
  - `push` 到 `main` 自动触发
  - 支持 `workflow_dispatch` 手动触发
  - 使用 `ubuntu-latest`
  - 配置 JDK 17、Android SDK、Gradle 缓存
  - 运行 `:app:assembleDebug`
  - 上传 `app/build/outputs/apk/debug/USB采集卡.apk` 为 Artifact
- 工作流中运行时生成 `local.properties`，避免依赖本机 Windows SDK 路径。
- 工作流权限保持最小化，仅使用 `contents: read`，当前模块不涉及创建 GitHub Release。

## 当前修改到哪个模块

模块十九：GitHub CI 自动构建 Debug APK。最小可用工作流已接入，等待首次远端运行验证。

## 具体修改的代码前后对比

### 1. 修改前仓库没有任何 GitHub Actions 工作流

修改前：

```text
.github/workflows/ 不存在
```

修改后新增：

```text
.github/workflows/android-debug-apk.yml
```

### 2. 修改前需要手动本地打包

修改前依赖本地命令：

```powershell
.\gradlew.bat :app:assembleDebug
```

修改后由 GitHub Actions 自动执行对应 Linux 命令：

```yaml
- name: Build debug APK
  run: ./gradlew --no-daemon :app:assembleDebug
```

### 3. 修改前 CI 环境没有 Android SDK 路径来源

修改后在 workflow 内显式生成：

```yaml
- name: Prepare local.properties for CI
  shell: bash
  run: |
    SDK_PATH="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
    if [ -z "$SDK_PATH" ]; then
      echo "ANDROID_SDK_ROOT is not available"
      exit 1
    fi
    printf "sdk.dir=%s\n" "$SDK_PATH" > local.properties
```

### 4. 修改后产物会被直接上传

```yaml
- name: Upload debug APK artifact
  uses: actions/upload-artifact@v4
  with:
    name: usb-capture-debug-apk
    path: app/build/outputs/apk/debug/USB采集卡.apk
    if-no-files-found: error
    retention-days: 14
```

## 验收标准

- `push` 到 `main` 后，GitHub Actions 自动触发 `Android Debug APK` workflow。
- workflow 能成功完成 Android 环境准备和 `:app:assembleDebug`。
- Actions 页面可下载名为 `usb-capture-debug-apk` 的 Artifact。
- Artifact 内包含 `USB采集卡.apk`。
- 仓库无需提交 `local.properties` 也能在 CI 中构建。

## 验证结果

- `git diff --check`：通过。
- `.\gradlew.bat :app:assembleDebug --offline`：`BUILD SUCCESSFUL`。
- 本地产物存在：`app/build/outputs/apk/debug/USB采集卡.apk`
- 当前 APK 大小：`26350363` bytes。
- 当前仓库新增文件：
  - `.github/workflows/android-debug-apk.yml`
  - `backup/019-GitHub CI自动打包前备份.md`
- 本机未安装可直接复用的 YAML 解析器：
  - `ConvertFrom-Yaml` 不可用
  - Python 缺少 `PyYAML`
- 因此 workflow 的最终语法有效性仍需依赖首次 GitHub 远端运行确认。

## 待办清单（未完成）

- 推送当前提交到 GitHub，观察首次 Actions 运行是否成功。
- 若远端缺少 Android 平台组件，按日志微调 `sdkmanager` 安装项。
- 确认 Artifact 下载后 APK 可正常安装。
- 评估是否进入下一模块：
  - `tag` 触发自动发布到 GitHub Releases
  - 接入 release keystore 自动签名

## 下一步要做什么

推送当前改动到 `main`，进入 GitHub 仓库的 Actions 页面检查首个 `Android Debug APK` 运行结果。如果首次运行成功，下一模块就可以继续做“打 tag 自动发布 Release APK”。
