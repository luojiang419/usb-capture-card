package com.codex.usbcapture

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val UPDATE_API_URL =
    "https://api.github.com/repos/luojiang419/usb-capture-card/releases/latest"
private const val UPDATE_RELEASE_PAGE_URL =
    "https://github.com/luojiang419/usb-capture-card/releases/latest"
private const val UPDATE_USER_AGENT = "USB-Capture-Updater/1.0"

data class AppUpdateAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long
)

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val tagName: String,
    val releaseName: String,
    val releaseNotes: String,
    val releasePageUrl: String,
    val asset: AppUpdateAsset
)

data class AppUpdateCheckResult(
    val currentVersion: String,
    val latestVersion: String,
    val releasePageUrl: String,
    val hasUpdate: Boolean,
    val updateInfo: AppUpdateInfo?
)

class AppUpdateManager(private val context: Context) {
    suspend fun checkLatest(currentVersion: String = BuildConfig.VERSION_NAME): AppUpdateCheckResult =
        withContext(Dispatchers.IO) {
            val release = fetchLatestRelease()
            val tagName = release.optString("tag_name").trim()
            if (tagName.isEmpty()) {
                throw IOException("GitHub Release 缺少版本标签")
            }

            val latestVersion = normalizeVersionString(tagName)
            val normalizedCurrentVersion = normalizeVersionString(currentVersion)
            val releasePageUrl = release.optString("html_url")
                .takeIf { it.isNotBlank() }
                ?: UPDATE_RELEASE_PAGE_URL
            val hasUpdate = compareVersions(latestVersion, normalizedCurrentVersion) > 0

            if (!hasUpdate) {
                return@withContext AppUpdateCheckResult(
                    currentVersion = normalizedCurrentVersion,
                    latestVersion = latestVersion,
                    releasePageUrl = releasePageUrl,
                    hasUpdate = false,
                    updateInfo = null
                )
            }

            val asset = selectApkAsset(release.optJSONArray("assets"))
                ?: throw IOException("最新 Release 没有可安装的 APK 资产")

            AppUpdateCheckResult(
                currentVersion = normalizedCurrentVersion,
                latestVersion = latestVersion,
                releasePageUrl = releasePageUrl,
                hasUpdate = true,
                updateInfo = AppUpdateInfo(
                    currentVersion = normalizedCurrentVersion,
                    latestVersion = latestVersion,
                    tagName = tagName,
                    releaseName = release.optString("name").ifBlank { tagName },
                    releaseNotes = release.optString("body"),
                    releasePageUrl = releasePageUrl,
                    asset = asset
                )
            )
        }

    suspend fun downloadUpdate(
        info: AppUpdateInfo,
        onProgress: (received: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
            "updates"
        )
        if (!updatesDir.exists() && !updatesDir.mkdirs()) {
            throw IOException("无法创建更新目录：${updatesDir.absolutePath}")
        }

        val targetFile = File(updatesDir, info.asset.name)
        if (targetFile.exists() && info.asset.size > 0L && targetFile.length() == info.asset.size) {
            onProgress(info.asset.size, info.asset.size)
            return@withContext targetFile
        }

        val partialFile = File(targetFile.absolutePath + ".part")
        if (targetFile.exists()) {
            targetFile.delete()
        }
        if (partialFile.exists()) {
            partialFile.delete()
        }

        val connection = openConnection(URL(info.asset.downloadUrl), accept = "application/octet-stream")
        try {
            val total = connection.contentLengthLong.coerceAtLeast(0L)
            connection.inputStream.use { input ->
                FileOutputStream(partialFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var received = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        received += read
                        onProgress(received, total)
                    }
                    output.flush()
                }
            }
        } finally {
            connection.disconnect()
        }

        if (partialFile.length() <= 0L) {
            partialFile.delete()
            throw IOException("更新包下载失败，文件为空")
        }

        if (targetFile.exists()) {
            targetFile.delete()
        }
        if (!partialFile.renameTo(targetFile)) {
            partialFile.delete()
            throw IOException("更新包落盘失败")
        }
        targetFile
    }

    private fun fetchLatestRelease(): JSONObject {
        val connection = openConnection(URL(UPDATE_API_URL), accept = "application/vnd.github+json")
        return try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: URL, accept: String): HttpURLConnection {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", UPDATE_USER_AGENT)
        }
        val statusCode = connection.responseCode
        if (statusCode !in 200..299) {
            val errorBody = runCatching {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrNull().orEmpty()
            connection.disconnect()
            throw IOException(
                buildString {
                    append("GitHub 更新请求失败：HTTP ")
                    append(statusCode)
                    if (errorBody.isNotBlank()) {
                        append(" - ")
                        append(errorBody.take(200))
                    }
                }
            )
        }
        return connection
    }

    private fun selectApkAsset(assets: JSONArray?): AppUpdateAsset? {
        if (assets == null) return null

        var fallback: AppUpdateAsset? = null
        for (index in 0 until assets.length()) {
            val item = assets.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            val downloadUrl = item.optString("browser_download_url").trim()
            if (!name.endsWith(".apk", ignoreCase = true) || downloadUrl.isBlank()) {
                continue
            }
            val asset = AppUpdateAsset(
                name = name,
                downloadUrl = downloadUrl,
                size = item.optLong("size", 0L)
            )
            if (name.contains("USB-Capture-Card", ignoreCase = true)) {
                return asset
            }
            if (fallback == null) {
                fallback = asset
            }
        }
        return fallback
    }
}

private fun normalizeVersionString(version: String): String {
    var normalized = version.trim()
    if (normalized.startsWith("refs/tags/")) {
        normalized = normalized.removePrefix("refs/tags/")
    }
    if (normalized.startsWith("v", ignoreCase = true)) {
        normalized = normalized.substring(1)
    }
    val buildIndex = normalized.indexOf('+')
    if (buildIndex >= 0) {
        normalized = normalized.substring(0, buildIndex)
    }
    return if (normalized.isBlank()) "0.0.0" else normalized
}

private fun compareVersions(left: String, right: String): Int {
    val leftParts = versionParts(normalizeVersionString(left))
    val rightParts = versionParts(normalizeVersionString(right))
    val maxSize = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until maxSize) {
        val leftPart = leftParts.getOrElse(index) { 0 }
        val rightPart = rightParts.getOrElse(index) { 0 }
        if (leftPart != rightPart) {
            return leftPart.compareTo(rightPart)
        }
    }
    return 0
}

private fun versionParts(version: String): List<Int> {
    return version
        .split('.', '-', '_')
        .mapNotNull { it.toIntOrNull() }
}
