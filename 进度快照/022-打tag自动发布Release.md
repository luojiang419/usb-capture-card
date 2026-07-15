# 022-打tag自动发布Release

## 已完成内容

- 读取最新快照 `021-修复gradlew脚本.md`，继续扩展 GitHub CI 到 Releases 自动发布。
- 创建阶段备份 `backup/021-打tag自动发布前备份.md`。
- 新增独立工作流 `.github/workflows/android-release-apk.yml`。
- 新工作流触发条件为：
  - `push` 标签 `v*`
- 工作流权限使用：

```yaml
permissions:
  contents: write
```

- Release 工作流已实现：
  - 检出代码
  - 配置 JDK 17、Android SDK、Gradle
  - 执行 `./gradlew --no-daemon :app:assembleDebug`
  - 将 `USB采集卡.apk` 复制并重命名为 `USB-Capture-Card-v版本号.apk`
  - 上传一份 Actions Artifact
  - 使用 `gh release create` 或 `gh release upload --clobber` 发布到 GitHub Releases
- 同步更新 `README.md`：
  - 说明 `push main` 只生成 Artifact
  - 说明 `git tag v1.4.1 && git push origin v1.4.1` 会自动发布 Release APK

## 当前修改到哪个模块

模块二十一：打 tag 自动发布 APK 到 GitHub Releases。配置已完成，等待推送后使用新 tag 实际验证。

## 具体修改的代码前后对比

### 1. 修改前只有 main 分支构建 Artifact

修改前仓库仅有：

```text
.github/workflows/android-debug-apk.yml
```

修改后新增：

```text
.github/workflows/android-release-apk.yml
```

### 2. 修改前工作流没有 Release 写权限

修改前：

```yaml
permissions:
  contents: read
```

修改后 Release 工作流改为：

```yaml
permissions:
  contents: write
```

### 3. 修改后 tag 推送会自动发布 APK

新增核心步骤：

```yaml
- name: Publish GitHub Release asset
  env:
    GH_TOKEN: ${{ github.token }}
    TAG_NAME: ${{ github.ref_name }}
    RELEASE_APK_PATH: ${{ steps.asset.outputs.release_apk_path }}
  run: |
    if gh release view "${TAG_NAME}" >/dev/null 2>&1; then
      gh release upload "${TAG_NAME}" "${RELEASE_APK_PATH}" --clobber
    else
      gh release create "${TAG_NAME}" "${RELEASE_APK_PATH}"
    fi
```

### 4. README 的发布说明已切换为自动流程

修改前仍是手动上传：

```text
创建版本标签，例如 v1.4.0。
将 APK 上传到对应 GitHub Release。
```

修改后改为：

```bash
git tag v1.4.1
git push origin v1.4.1
```

随后由 GitHub Actions 自动构建并发布。

## 验收标准

- `push main` 继续只生成 Actions Artifact，不自动污染 Releases。
- `push v1.4.1` 这类 tag 后，GitHub Actions 自动触发 `Android Release APK`。
- 工作流可成功创建或更新对应 tag 的 GitHub Release。
- Release 资产名为 `USB-Capture-Card-v1.4.1.apk` 这类稳定英文文件名。
- 如果同一 tag 重跑，资产可被 `--clobber` 覆盖更新。

## 验证结果

- `git diff --check`：通过，仅有 `README.md` 的 LF/CRLF 提示。
- 回读工作流确认：
  - 触发条件：`push tags: v*`
  - 权限：`contents: write`
  - 发布命令：`gh release create / gh release upload --clobber`
- 回读 `README.md` 确认发布说明已切换到自动流程。
- 当前尚未推送本次改动，也尚未创建新 tag，因此 Release 自动发布还未做真实远端验证。

## 待办清单（未完成）

- 提交并推送本次 Release 工作流改动。
- 创建测试标签，例如 `v1.4.1-test` 或正式版本标签。
- 观察 `Android Release APK` workflow 是否成功。
- 确认新 Release 页面中能看到英文命名的 APK 资产。

## 下一步要做什么

提交并推送当前改动，然后创建并推送一个新 tag。最直接的验证方式是：

```bash
git tag v1.4.1
git push origin v1.4.1
```

随后检查 GitHub Releases 页面是否自动出现对应 APK。
