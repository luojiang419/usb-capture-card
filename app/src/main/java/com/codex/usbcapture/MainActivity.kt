package com.codex.usbcapture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Matrix
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.doOnLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jiangdg.ausbc.CameraClient
import com.jiangdg.ausbc.base.CameraActivity
import com.jiangdg.ausbc.camera.CameraUvcStrategy
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.camera.bean.CameraStatus
import com.jiangdg.ausbc.callback.ICaptureCallBack
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.render.env.RotateType
import com.jiangdg.ausbc.utils.bus.BusKey
import com.jiangdg.ausbc.utils.bus.EventBus
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.IAspectRatio
import com.serenegiant.usb.USBMonitor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay

private const val DEFAULT_FRAME_RATE = 30
private const val CONTROLS_AUTO_HIDE_MS = 3000L
private const val FIRST_FRAME_TIMEOUT_MS = 5000L
private const val FRAME_STALL_TIMEOUT_MS = 3500L
private const val PREVIEW_WATCHDOG_INTERVAL_MS = 1000L
private const val MAX_PREVIEW_RECOVERY_ATTEMPTS = 1
private const val MIN_PREVIEW_ZOOM = 1f
private const val MAX_PREVIEW_ZOOM = 5f
private const val PREVIEW_ZOOM_SNAP_THRESHOLD = 1.03f
private const val TAG = "UsbCapture"
private val FRAME_RATE_OPTIONS = listOf(24, 30, 60)

class MainActivity : CameraActivity() {
    private lateinit var root: FrameLayout
    private lateinit var cameraContainer: FrameLayout
    private lateinit var cameraView: AspectRatioTextureView
    private var cameraClient: CameraClient? = null
    private var uvcStrategy: CameraUvcStrategy? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recordingStartedAt = 0L
    private var activeRecordingFile: File? = null
    private val previewFrameCount = AtomicInteger(0)
    @Volatile
    private var previewFpsStartedAt = 0L
    @Volatile
    private var previewFpsEnabled = false
    @Volatile
    private var firstPreviewFrameReceived = false
    @Volatile
    private var lastPreviewFrameAt = 0L
    private var previewRecoveryAttempts = 0
    private var previewRecoveryInProgress = false
    private var cachedPreviewSizes: List<PreviewOption> = emptyList()
    private val previewTransformMatrix = Matrix()
    private var previewZoom = MIN_PREVIEW_ZOOM
    private var isPreviewZoomed by mutableStateOf(false)
    private var previewPanX = 0f
    private var previewPanY = 0f

    private var themeMode by mutableStateOf(ThemeMode.System)
    private var showControls by mutableStateOf(true)
    private var showFunctionMenu by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var isScreenLocked by mutableStateOf(false)
    private var controlsInteractionTick by mutableStateOf(0)
    private var screenOrientation by mutableStateOf(ScreenOrientation.Portrait)
    private var uiState by mutableStateOf(UsbCameraUiState())
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.i(TAG, "Camera permission granted")
            openCameraAfterPermissionGrant()
        } else {
            Log.w(TAG, "Camera permission denied")
            uiState = uiState.copy(
                connectionState = DeviceConnectionState.Error,
                statusText = "缺少相机权限",
                errorMessage = "缺少相机权限，无法显示采集画面",
                diagnosticText = "应用层：相机权限未授权"
            )
        }
    }
    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && uiState.isPreviewing && !uiState.isRecording) {
            Log.i(TAG, "Audio permission granted, starting recording")
            startRecording()
            hideControlsForPreview()
        } else if (!granted) {
            Log.w(TAG, "Audio permission denied")
            revealControls()
            uiState = uiState.copy(
                statusText = "录制需要录音权限",
                errorMessage = "录音权限被拒绝，实时预览仍可正常使用"
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        themeMode = loadThemeMode()
        screenOrientation = ScreenOrientation.from(resources.configuration.orientation)
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        onBackPressedDispatcher.addCallback(this, createBackCallback())
        refreshRecordingFiles()
        ensureCameraPermission()
        enterImmersiveMode()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        screenOrientation = ScreenOrientation.from(newConfig.orientation)
        refreshRenderSize()
    }

    override fun onDestroy() {
        if (uiState.isRecording) {
            runCatching { cameraClient?.captureVideoStop() }
        }
        stopRecordingTicker()
        stopPreviewFps()
        mainHandler.removeCallbacks(previewWatchdog)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && uiState.isPreviewing && !showControls && !showSettings) {
            enterImmersiveMode()
        }
    }

    override fun getRootView(layoutInflater: LayoutInflater): View {
        root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        cameraContainer = FrameLayout(this)
        val composeOverlay = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                UsbCaptureTheme(themeMode) {
                    UsbCaptureScreen(
                        uiState = uiState,
                        themeMode = themeMode,
                        screenOrientation = screenOrientation,
                        controlsVisible = showControls,
                        functionMenuExpanded = showFunctionMenu,
                        showSettings = showSettings,
                        screenLocked = isScreenLocked,
                        singleFingerPanEnabled = isPreviewZoomed,
                        controlsInteractionTick = controlsInteractionTick,
                        onThemeModeChange = ::switchTheme,
                        onRecordClick = ::handleRecordClick,
                        onResolutionSelected = ::updateResolution,
                        onAutoDetectResolutions = ::autoDetectPreviewSizes,
                        onFrameRateSelected = ::updateFrameRate,
                        onFunctionMenuExpandedChange = ::setFunctionMenuExpanded,
                        onSettingsClick = ::showCaptureSettings,
                        onSettingsDismiss = ::dismissCaptureSettings,
                        onExitClick = ::exitCaptureScreen,
                        onPreviewTap = ::handlePreviewTap,
                        onPreviewTransform = ::handlePreviewTransform,
                        onAutoHideControls = ::autoHideControls,
                        onToggleScreenLock = ::toggleScreenLock
                    )
                }
            }
        }
        root.addView(
            cameraContainer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            composeOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        return root
    }

    override fun getCameraView(): IAspectRatio {
        cameraView = AspectRatioTextureView(this)
        return cameraView
    }

    override fun getCameraViewContainer(): ViewGroup = cameraContainer

    override fun getCameraClient(): CameraClient {
        val request = CameraRequest.Builder()
            .setPreviewWidth(1280)
            .setPreviewHeight(720)
            .setContinuousAFModel(true)
            .setContinuousAutoModel(true)
            .create()

        val strategy = CameraUvcStrategy(this).also { strategy ->
            uvcStrategy = strategy
            strategy.setDeviceConnectStatusListener(createDeviceConnectCallback())
        }

        return CameraClient.newBuilder(this)
            .setEnableGLES(true)
            .setRawImage(false)
            .setCameraStrategy(strategy)
            .setCameraRequest(request)
            .setDefaultRotateType(RotateType.ANGLE_0)
            .openDebug(true)
            .build()
            .also { client ->
                cameraClient = client
                client.addPreviewDataCallBack(previewDataCallback)
            }
    }

    override fun initData() {
        super.initData()
        EventBus.with<CameraStatus>(BusKey.KEY_CAMERA_STATUS).observe(this) { status ->
            handleCameraStatus(status)
        }
    }

    private fun handleCameraStatus(status: CameraStatus) {
        Log.i(TAG, "Camera status code=${status.code}, message=${status.message.orEmpty()}")
        val details = collectUsbDetails(
            refreshPreviewSizes = status.code == CameraStatus.START ||
                status.code == CameraStatus.ERROR_PREVIEW_SIZE
        )
        uiState = when (status.code) {
            CameraStatus.START -> {
                previewRecoveryInProgress = false
                resetPreviewFps()
                refreshRenderSize()
                hideControlsForPreview()
                uiState.copy(
                    connectionState = DeviceConnectionState.Connecting,
                    statusText = "相机已启动，正在等待视频帧",
                    isPreviewing = true,
                    captureFps = 0,
                    errorMessage = null,
                    diagnosticText = "采集层：等待首帧（最长 5 秒）",
                    deviceName = details.deviceName,
                    previewSizeText = details.previewSizeText,
                    previewSizes = details.previewSizes
                )
            }
            CameraStatus.STOP -> {
                stopPreviewFps()
                if (previewRecoveryInProgress) {
                    uiState.copy(
                        connectionState = DeviceConnectionState.Connecting,
                        statusText = "正在重新启动采集画面",
                        isPreviewing = false,
                        isRecording = false,
                        captureFps = 0,
                        diagnosticText = "采集层：自动恢复进行中",
                        deviceName = details.deviceName,
                        previewSizes = details.previewSizes
                    )
                } else {
                    showMainScreenChrome()
                    uiState.copy(
                        connectionState = DeviceConnectionState.Waiting,
                        statusText = "等待采集卡",
                        isPreviewing = false,
                        isRecording = false,
                        captureFps = 0,
                        diagnosticText = "采集层：预览已停止",
                        deviceName = details.deviceName,
                        previewSizes = details.previewSizes
                    )
                }
            }
            CameraStatus.ERROR_PREVIEW_SIZE -> {
                stopPreviewFps()
                isScreenLocked = false
                revealControls()
                uiState.copy(
                    connectionState = DeviceConnectionState.Connecting,
                    statusText = "正在匹配可用分辨率",
                    errorMessage = localizeCameraMessage(status.message, "正在匹配可用分辨率"),
                    diagnosticText = "采集层：当前分辨率不可用",
                    previewSizes = details.previewSizes
                )
            }
            else -> {
                stopPreviewFps()
                showMainScreenChrome()
                uiState.copy(
                    connectionState = DeviceConnectionState.Error,
                    statusText = "连接失败",
                    isPreviewing = false,
                    isRecording = false,
                    captureFps = 0,
                    errorMessage = localizeCameraMessage(status.message, "未知错误"),
                    diagnosticText = "采集层：相机启动失败（状态码 ${status.code}）",
                    deviceName = details.deviceName,
                    previewSizes = details.previewSizes
                )
            }
        }
    }

    private fun handleRecordClick() {
        showFunctionMenu = false
        if (!uiState.isPreviewing) {
            revealControls()
            uiState = uiState.copy(errorMessage = "未检测到视频信号")
            return
        }
        if (uiState.isRecording) {
            stopRecording()
        } else if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            uiState = uiState.copy(
                statusText = "等待录音权限",
                errorMessage = null
            )
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startRecording()
        }
        hideControlsForPreview()
    }

    private fun switchTheme(mode: ThemeMode) {
        themeMode = mode
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.name)
            .apply()
    }

    private fun updateResolution(option: PreviewOption) {
        resetPreviewTransform()
        val changed = cameraClient?.updateResolution(option.width, option.height) == true
        uiState = uiState.copy(
            connectionState = if (changed) DeviceConnectionState.Connecting else uiState.connectionState,
            statusText = if (changed) "正在切换分辨率" else uiState.statusText,
            previewSizeText = option.label,
            errorMessage = if (changed) null else "当前无法切换分辨率"
        )
    }

    private fun autoDetectPreviewSizes() {
        val detected = detectPreviewSizes()
        uiState = uiState.copy(
            previewSizes = detected,
            statusText = if (detected.isEmpty()) "未检测到可用分辨率" else "已检测到 ${detected.size} 个可用分辨率",
            errorMessage = if (detected.isEmpty()) "未检测到可用分辨率" else null
        )
    }

    private fun detectPreviewSizes(): List<PreviewOption> {
        val client = cameraClient ?: return cachedPreviewSizes
        val sizes = linkedSetOf<PreviewOption>()
        runCatching { client.getAllPreviewSizes(null) }
            .onFailure { Log.w(TAG, "Preview size query failed", it) }
            .getOrNull()
            ?.mapTo(sizes) { PreviewOption(it.width, it.height) }
        val request = client.getCameraRequest()
        if (request != null) {
            sizes.add(PreviewOption(request.previewWidth, request.previewHeight))
        }
        return sizes
            .filter { it.width > 0 && it.height > 0 }
            .sortedWith(compareByDescending<PreviewOption> { it.width * it.height }.thenByDescending { it.width })
            .also { cachedPreviewSizes = it }
    }

    private fun updateFrameRate(frameRate: Int) {
        uiState = uiState.copy(
            selectedFrameRate = frameRate,
            statusText = "帧速率已选择 ${frameRate}帧/秒",
            errorMessage = null
        )
    }

    private fun setFunctionMenuExpanded(expanded: Boolean) {
        if (isScreenLocked) return
        showFunctionMenu = expanded
        if (expanded) {
            showControls = true
        }
        markControlsInteraction()
    }

    private fun showCaptureSettings() {
        if (isScreenLocked) return
        showFunctionMenu = false
        showControls = true
        showSettings = true
        markControlsInteraction()
    }

    private fun dismissCaptureSettings() {
        showSettings = false
        showFunctionMenu = false
        if (uiState.isPreviewing) {
            hideControlsForPreview()
        } else {
            revealControls()
        }
    }

    private fun exitCaptureScreen() {
        showFunctionMenu = false
        if (uiState.isRecording) {
            runCatching { cameraClient?.captureVideoStop() }
        }
        finish()
    }

    private fun createBackCallback(): OnBackPressedCallback {
        return object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleCaptureBack()
            }
        }
    }

    private fun handleCaptureBack() {
        if (showFunctionMenu) {
            showFunctionMenu = false
            showControls = true
            markControlsInteraction()
            return
        }
        if (showSettings) {
            showSettings = false
            showControls = true
            markControlsInteraction()
            return
        }
        if (isScreenLocked) {
            return
        }
        if (uiState.isPreviewing) {
            if (showControls) {
                hideControlsForPreview()
            } else {
                revealControls()
            }
            return
        }
        finish()
    }

    private fun revealControls() {
        if (isScreenLocked) return
        showControls = true
        markControlsInteraction()
    }

    private fun hideControlsForPreview() {
        showControls = false
        showFunctionMenu = false
        showSettings = false
        enterImmersiveMode()
    }

    private fun showMainScreenChrome() {
        isScreenLocked = false
        showFunctionMenu = false
        showSettings = false
        showControls = true
        markControlsInteraction()
        exitImmersiveMode()
    }

    private fun enterImmersiveMode() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun exitImmersiveMode() {
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    private fun handlePreviewTap() {
        if (!uiState.isPreviewing || showSettings || isScreenLocked) return
        revealControls()
    }

    private fun handlePreviewTransform(
        zoomChange: Float,
        panChangeX: Float,
        panChangeY: Float,
        centroidX: Float,
        centroidY: Float
    ) {
        if (!uiState.isPreviewing || isScreenLocked || !::cameraView.isInitialized) return
        if (
            !zoomChange.isFinite() ||
            zoomChange <= 0f ||
            !panChangeX.isFinite() ||
            !panChangeY.isFinite() ||
            !centroidX.isFinite() ||
            !centroidY.isFinite()
        ) return
        val oldZoom = previewZoom
        val scaledZoom = (oldZoom * zoomChange).coerceIn(MIN_PREVIEW_ZOOM, MAX_PREVIEW_ZOOM)
        val newZoom = if (scaledZoom <= PREVIEW_ZOOM_SNAP_THRESHOLD) {
            MIN_PREVIEW_ZOOM
        } else {
            scaledZoom
        }
        val appliedZoomChange = newZoom / oldZoom
        val localCentroidX = centroidX - cameraView.left
        val localCentroidY = centroidY - cameraView.top
        val centerX = cameraView.width / 2f
        val centerY = cameraView.height / 2f
        previewPanX = previewPanX * appliedZoomChange +
            (1f - appliedZoomChange) * (localCentroidX - centerX) + panChangeX
        previewPanY = previewPanY * appliedZoomChange +
            (1f - appliedZoomChange) * (localCentroidY - centerY) + panChangeY
        previewZoom = newZoom
        isPreviewZoomed = newZoom > MIN_PREVIEW_ZOOM
        if (!isPreviewZoomed) {
            previewPanX = 0f
            previewPanY = 0f
        }
        applyPreviewTransform(cameraView.width, cameraView.height)
    }

    private fun resetPreviewTransform() {
        previewZoom = MIN_PREVIEW_ZOOM
        isPreviewZoomed = false
        previewPanX = 0f
        previewPanY = 0f
        if (::cameraView.isInitialized && cameraView.width > 0 && cameraView.height > 0) {
            applyPreviewTransform(cameraView.width, cameraView.height)
        }
    }

    private fun autoHideControls() {
        if (!uiState.isPreviewing || showSettings || showFunctionMenu || isScreenLocked) return
        hideControlsForPreview()
    }

    private fun toggleScreenLock() {
        if (!uiState.isPreviewing) return
        isScreenLocked = !isScreenLocked
        showFunctionMenu = false
        showSettings = false
        if (isScreenLocked) {
            showControls = false
            enterImmersiveMode()
        } else {
            showControls = true
            markControlsInteraction()
        }
    }

    private fun markControlsInteraction() {
        controlsInteractionTick += 1
    }

    private fun resetPreviewFps() {
        previewFpsEnabled = true
        firstPreviewFrameReceived = false
        lastPreviewFrameAt = 0L
        previewFrameCount.set(0)
        previewFpsStartedAt = SystemClock.elapsedRealtime()
        uiState = uiState.copy(captureFps = 0)
        mainHandler.removeCallbacks(previewWatchdog)
        mainHandler.postDelayed(previewWatchdog, PREVIEW_WATCHDOG_INTERVAL_MS)
    }

    private fun stopPreviewFps() {
        previewFpsEnabled = false
        firstPreviewFrameReceived = false
        lastPreviewFrameAt = 0L
        previewFrameCount.set(0)
        previewFpsStartedAt = 0L
        mainHandler.removeCallbacks(previewWatchdog)
    }

    private fun onPreviewFrameArrived(
        data: ByteArray,
        format: IPreviewDataCallBack.DataFormat
    ) {
        if (!previewFpsEnabled) return
        val now = SystemClock.elapsedRealtime()
        val isFirstFrame = !firstPreviewFrameReceived
        firstPreviewFrameReceived = true
        lastPreviewFrameAt = now
        if (isFirstFrame) {
            val averageLuma = estimateAverageLuma(data)
            Log.i(
                TAG,
                "First preview frame format=$format, bytes=${data.size}, averageLuma=$averageLuma"
            )
            runOnUiThread {
                if (previewFpsEnabled && uiState.isPreviewing) {
                    hideControlsForPreview()
                    uiState = uiState.copy(
                        connectionState = DeviceConnectionState.Connected,
                        statusText = "视频帧已连接",
                        errorMessage = null,
                        diagnosticText = if (averageLuma <= 24) {
                            "帧流已到达，但内容接近黑场（亮度 $averageLuma）"
                        } else {
                            "帧流正常（$format，亮度 $averageLuma）"
                        }
                    )
                }
            }
        }
        if (previewFpsStartedAt == 0L) {
            previewFpsStartedAt = now
        }
        previewFrameCount.incrementAndGet()
        val elapsed = now - previewFpsStartedAt
        if (elapsed >= 1000L) {
            val fps = ((previewFrameCount.getAndSet(0) * 1000L) / elapsed).toInt()
            previewFpsStartedAt = now
            runOnUiThread {
                if (uiState.isPreviewing) {
                    uiState = uiState.copy(captureFps = fps)
                }
            }
        }
    }

    private fun estimateAverageLuma(data: ByteArray): Int {
        val lumaSize = data.size * 2 / 3
        if (lumaSize <= 0) return 0
        val sampleStep = maxOf(1, lumaSize / 512)
        var sum = 0L
        var samples = 0
        var index = 0
        while (index < lumaSize) {
            sum += data[index].toInt() and 0xFF
            samples += 1
            index += sampleStep
        }
        return if (samples == 0) 0 else (sum / samples).toInt()
    }

    private val previewWatchdog = object : Runnable {
        override fun run() {
            if (!previewFpsEnabled) return
            val now = SystemClock.elapsedRealtime()
            val receivedFrame = firstPreviewFrameReceived
            val referenceTime = if (receivedFrame) lastPreviewFrameAt else previewFpsStartedAt
            val timeout = if (receivedFrame) FRAME_STALL_TIMEOUT_MS else FIRST_FRAME_TIMEOUT_MS
            if (referenceTime > 0L && now - referenceTime >= timeout) {
                handlePreviewTimeout(receivedFrame)
                return
            }
            mainHandler.postDelayed(this, PREVIEW_WATCHDOG_INTERVAL_MS)
        }
    }

    private fun handlePreviewTimeout(hadFrames: Boolean) {
        val reason = if (hadFrames) "preview frame stream stalled" else "first preview frame timed out"
        Log.w(
            TAG,
            "$reason, cameraOpened=${cameraClient?.isCameraOpened()}, recovery=$previewRecoveryAttempts"
        )
        if (!uiState.isRecording && previewRecoveryAttempts < MAX_PREVIEW_RECOVERY_ATTEMPTS) {
            previewRecoveryAttempts += 1
            previewRecoveryInProgress = true
            uiState = uiState.copy(
                connectionState = DeviceConnectionState.Connecting,
                statusText = "画面异常，正在自动恢复",
                captureFps = 0,
                errorMessage = null,
                diagnosticText = if (hadFrames) {
                    "采集层：视频帧中断，执行第 1 次恢复"
                } else {
                    "采集层：相机已启动但无首帧，执行第 1 次恢复"
                }
            )
            val request = cameraClient?.getCameraRequest()
            val restarted = request != null &&
                cameraClient?.updateResolution(request.previewWidth, request.previewHeight) == true
            if (restarted) return
            previewRecoveryInProgress = false
            Log.e(TAG, "Preview recovery request failed")
        }

        showMainScreenChrome()
        firstPreviewFrameReceived = false
        lastPreviewFrameAt = 0L
        uiState = uiState.copy(
            connectionState = DeviceConnectionState.Error,
            statusText = "采集画面异常",
            captureFps = 0,
            errorMessage = if (hadFrames) {
                "视频帧已中断，请检查 USB 供电、线材或重新插拔采集卡"
            } else {
                "相机已打开但未收到视频帧，请检查采集卡输入信号"
            },
            diagnosticText = if (hadFrames) {
                "采集层：曾收到帧，随后断流"
            } else {
                "采集层：USB/UVC 已启动，但始终没有帧数据"
            }
        )
    }

    private val previewDataCallback = object : IPreviewDataCallBack {
        override fun onPreviewData(data: ByteArray?, format: IPreviewDataCallBack.DataFormat) {
            if (data != null && data.isNotEmpty()) {
                onPreviewFrameArrived(data, format)
            }
        }
    }

    private fun startRecording() {
        val client = cameraClient
        if (client == null) {
            uiState = uiState.copy(errorMessage = "相机尚未初始化")
            return
        }
        val target = createRecordingFile()
        activeRecordingFile = target
        uiState = uiState.copy(statusText = "正在准备录制", errorMessage = null)
        client.captureVideoStart(
            object : ICaptureCallBack {
                override fun onBegin() {
                    runOnUiThread {
                        recordingStartedAt = SystemClock.elapsedRealtime()
                        uiState = uiState.copy(
                            isRecording = true,
                            recordingSeconds = 0L,
                            statusText = "正在录制",
                            errorMessage = null
                        )
                        startRecordingTicker()
                    }
                }

                override fun onError(error: String?) {
                    runOnUiThread {
                        stopRecordingTicker()
                        activeRecordingFile = null
                        uiState = uiState.copy(
                            isRecording = false,
                            statusText = "录制失败",
                            errorMessage = localizeCameraMessage(error, "录制失败")
                        )
                        refreshRecordingFiles()
                    }
                }

                override fun onComplete(path: String?) {
                    runOnUiThread {
                        stopRecordingTicker()
                        activeRecordingFile = null
                        uiState = uiState.copy(
                            isRecording = false,
                            statusText = "录制已保存",
                            errorMessage = null
                        )
                        refreshRecordingFiles()
                    }
                }
            },
            target.absolutePath
        )
    }

    private fun stopRecording() {
        uiState = uiState.copy(statusText = "正在停止录制")
        cameraClient?.captureVideoStop()
    }

    private fun startRecordingTicker() {
        mainHandler.removeCallbacks(recordingTicker)
        mainHandler.post(recordingTicker)
    }

    private fun stopRecordingTicker() {
        mainHandler.removeCallbacks(recordingTicker)
    }

    private val recordingTicker = object : Runnable {
        override fun run() {
            if (!uiState.isRecording) return
            val seconds = (SystemClock.elapsedRealtime() - recordingStartedAt) / 1000L
            uiState = uiState.copy(recordingSeconds = seconds)
            mainHandler.postDelayed(this, 1000L)
        }
    }

    private fun ensureCameraPermission() {
        if (hasPermission(Manifest.permission.CAMERA)) {
            Log.i(TAG, "Camera permission already granted; waiting for the base surface callback")
            uiState = uiState.copy(
                statusText = "正在检测采集卡",
                diagnosticText = "应用层：相机权限已授权，等待 USB/UVC"
            )
            return
        }
        uiState = uiState.copy(
            connectionState = DeviceConnectionState.Connecting,
            statusText = "等待相机权限授权",
            diagnosticText = "应用层：等待相机权限"
        )
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun openCameraAfterPermissionGrant() {
        uiState = uiState.copy(
            connectionState = DeviceConnectionState.Connecting,
            statusText = "正在检测采集卡",
            errorMessage = null,
            diagnosticText = "应用层：相机权限已授权，等待 USB/UVC"
        )
        if (
            ::cameraView.isInitialized &&
            cameraView.isAvailable &&
            cameraClient?.isCameraOpened() != true
        ) {
            Log.i(TAG, "Surface is already available after permission grant; opening camera once")
            cameraClient?.openCamera(cameraView, false)
            refreshRenderSize()
        }
    }

    private fun refreshRenderSize() {
        if (!::cameraView.isInitialized) return
        val client = cameraClient ?: return
        val oldWidth = cameraView.width
        val oldHeight = cameraView.height
        client.getCameraRequest()?.let { request ->
            cameraView.setAspectRatio(request.previewWidth, request.previewHeight)
        }
        cameraContainer.requestLayout()
        cameraView.doOnLayout { view ->
            if (view.width > 0 && view.height > 0) {
                Log.i(
                    TAG,
                    "Render layout updated for ${screenOrientation.name}: " +
                        "$oldWidth x $oldHeight -> ${view.width} x ${view.height}"
                )
                client.setRenderSize(view.width, view.height)
                applyPreviewTransform(view.width, view.height)
            }
        }
    }

    private fun applyPreviewTransform(viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0) return
        val request = cameraClient?.getCameraRequest() ?: return
        if (request.previewWidth <= 0 || request.previewHeight <= 0) return

        val sourceAspect = request.previewWidth.toFloat() / request.previewHeight
        val viewAspect = viewWidth.toFloat() / viewHeight
        val baseScaleX: Float
        val baseScaleY: Float
        if (sourceAspect > viewAspect) {
            baseScaleX = 1f
            baseScaleY = viewAspect / sourceAspect
        } else {
            baseScaleX = sourceAspect / viewAspect
            baseScaleY = 1f
        }

        val scaleX = baseScaleX * previewZoom
        val scaleY = baseScaleY * previewZoom
        val contentWidth = viewWidth * scaleX
        val contentHeight = viewHeight * scaleY
        val maxPanX = maxOf(0f, (contentWidth - viewWidth) / 2f)
        val maxPanY = maxOf(0f, (contentHeight - viewHeight) / 2f)
        previewPanX = previewPanX.coerceIn(-maxPanX, maxPanX)
        previewPanY = previewPanY.coerceIn(-maxPanY, maxPanY)

        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f
        previewTransformMatrix.setValues(
            floatArrayOf(
                scaleX,
                0f,
                centerX * (1f - scaleX) + previewPanX,
                0f,
                scaleY,
                centerY * (1f - scaleY) + previewPanY,
                0f,
                0f,
                1f
            )
        )
        cameraView.setTransform(previewTransformMatrix)
    }

    private fun hasPermission(permission: String): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun createDeviceConnectCallback(): IDeviceConnectCallBack {
        return object : IDeviceConnectCallBack {
            override fun onAttachDev(device: UsbDevice?) {
                Log.i(TAG, "USB attached: ${usbDeviceSummary(device)}")
                runOnUiThread {
                    resetPreviewTransform()
                    previewRecoveryAttempts = 0
                    previewRecoveryInProgress = false
                    cachedPreviewSizes = emptyList()
                    revealControls()
                    uiState = uiState.copy(
                        connectionState = DeviceConnectionState.Connecting,
                        statusText = "检测到采集卡",
                        deviceName = deviceLabel(device),
                        errorMessage = null,
                        diagnosticText = "USB 层：设备已枚举，等待授权"
                    )
                }
            }

            override fun onDetachDec(device: UsbDevice?) {
                Log.w(TAG, "USB detached: ${usbDeviceSummary(device)}")
                runOnUiThread {
                    resetPreviewTransform()
                    previewRecoveryAttempts = 0
                    previewRecoveryInProgress = false
                    cachedPreviewSizes = emptyList()
                    stopRecordingTicker()
                    stopPreviewFps()
                    showMainScreenChrome()
                    uiState = uiState.copy(
                        connectionState = DeviceConnectionState.Waiting,
                        statusText = "采集卡已拔出",
                        deviceName = deviceLabel(device),
                        isPreviewing = false,
                        isRecording = false,
                        diagnosticText = "USB 层：设备已物理拔出"
                    )
                }
            }

            override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                Log.i(TAG, "USB permission granted: ${usbDeviceSummary(device)}")
                runOnUiThread {
                    val details = collectUsbDetails(device)
                    uiState = uiState.copy(
                        connectionState = DeviceConnectionState.Connecting,
                        statusText = "采集卡权限已授权",
                        deviceName = details.deviceName,
                        previewSizeText = details.previewSizeText,
                        previewSizes = details.previewSizes,
                        errorMessage = null,
                        diagnosticText = "USB 层：设备权限已授权，正在启动 UVC"
                    )
                }
            }

            override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                Log.w(TAG, "USB disconnected: ${usbDeviceSummary(device)}")
                runOnUiThread {
                    resetPreviewTransform()
                    previewRecoveryAttempts = 0
                    previewRecoveryInProgress = false
                    cachedPreviewSizes = emptyList()
                    stopRecordingTicker()
                    stopPreviewFps()
                    showMainScreenChrome()
                    uiState = uiState.copy(
                        connectionState = DeviceConnectionState.Waiting,
                        statusText = "采集卡连接已断开",
                        deviceName = deviceLabel(device),
                        isPreviewing = false,
                        isRecording = false,
                        diagnosticText = "USB 层：设备连接已断开"
                    )
                }
            }

            override fun onCancelDev(device: UsbDevice?) {
                Log.w(TAG, "USB permission cancelled: ${usbDeviceSummary(device)}")
                runOnUiThread {
                    revealControls()
                    uiState = uiState.copy(
                        connectionState = DeviceConnectionState.Error,
                        statusText = "采集卡权限被取消",
                        deviceName = deviceLabel(device),
                        errorMessage = "采集卡权限被取消",
                        diagnosticText = "USB 层：设备权限未授权"
                    )
                }
            }
        }
    }

    private fun collectUsbDetails(
        preferredDevice: UsbDevice? = null,
        refreshPreviewSizes: Boolean = false
    ): UsbCameraDetails {
        val request = cameraClient?.getCameraRequest()
        val sizes = if (refreshPreviewSizes) detectPreviewSizes() else cachedPreviewSizes
        val currentDevice = preferredDevice
            ?: uvcStrategy?.getCurrentDevice()
            ?: uvcStrategy?.getUsbDeviceList()?.firstOrNull()
        val previewSize = request?.let { "${it.previewWidth} × ${it.previewHeight}" }
            ?: sizes.firstOrNull()?.label
            ?: uiState.previewSizeText
        return UsbCameraDetails(
            deviceName = deviceLabel(currentDevice),
            previewSizeText = previewSize,
            previewSizes = sizes
        )
    }

    private fun deviceLabel(device: UsbDevice?): String {
        return if (device == null) "采集卡" else "采集卡已连接"
    }

    private fun usbDeviceSummary(device: UsbDevice?): String {
        if (device == null) return "unknown"
        return "vid=0x${device.vendorId.toString(16)}, pid=0x${device.productId.toString(16)}, " +
            "class=${device.deviceClass}, id=${device.deviceId}"
    }

    private fun localizeCameraMessage(message: String?, fallback: String): String {
        val text = message?.trim().orEmpty()
        if (text.isBlank()) return fallback
        val lower = text.lowercase(Locale.US)
        return when {
            lower.contains("find no uvc devices") || lower.contains("getusbdevicelist") -> {
                "未发现采集卡，请连接采集卡后重试"
            }
            lower.contains("permission") -> "缺少采集卡或相机权限"
            lower.contains("preview") && lower.contains("size") -> "当前分辨率不可用，请自动检测后重新选择"
            lower.contains("open") && lower.contains("camera") -> "打开采集画面失败"
            lower.contains("disconnect") || lower.contains("detach") -> "采集卡连接已断开"
            lower.contains("error") || lower.any { it in 'a'..'z' } -> fallback
            else -> text
        }
    }

    private fun createRecordingFile(): File {
        val dir = recordingDirectory()
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir, "采集录像_$stamp.mp4")
    }

    private fun recordingDirectory(): File {
        val base = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        return File(base, "UsbCapture")
    }

    private fun refreshRecordingFiles() {
        val files = recordingDirectory()
            .listFiles { file -> file.isFile && file.extension.equals("mp4", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map {
                RecordingFile(
                    name = it.name,
                    path = it.absolutePath,
                    sizeBytes = it.length(),
                    createdAtMillis = it.lastModified()
                )
            }
            .orEmpty()
        uiState = uiState.copy(recordingFiles = files)
    }

    private fun openRecording(file: RecordingFile) {
        val target = File(file.path)
        if (!target.exists()) {
            uiState = uiState.copy(errorMessage = "录制文件不存在")
            refreshRecordingFiles()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", target)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
            .onFailure { uiState = uiState.copy(errorMessage = "没有可用播放器") }
    }

    private fun loadThemeMode(): ThemeMode {
        val saved = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_THEME_MODE, ThemeMode.System.name)
        return ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.System
    }

    companion object {
        private const val PREFS_NAME = "usb_capture_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}

enum class ThemeMode(val label: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("暗黑")
}

enum class DeviceConnectionState {
    Waiting,
    Connecting,
    Connected,
    Error
}

enum class ScreenOrientation(val label: String) {
    Portrait("竖屏"),
    Landscape("横屏");

    companion object {
        fun from(value: Int): ScreenOrientation {
            return if (value == Configuration.ORIENTATION_LANDSCAPE) {
                Landscape
            } else {
                Portrait
            }
        }
    }
}

data class RecordingFile(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val createdAtMillis: Long,
    val durationText: String = "--:--"
) {
    val displayName: String = name.substringBeforeLast(".")
}

data class PreviewOption(
    val width: Int,
    val height: Int
) {
    val label: String = "$width × $height"
}

private data class UsbCameraDetails(
    val deviceName: String,
    val previewSizeText: String,
    val previewSizes: List<PreviewOption>
)

data class UsbCameraUiState(
    val connectionState: DeviceConnectionState = DeviceConnectionState.Waiting,
    val statusText: String = "等待采集卡",
    val deviceName: String = "采集卡",
    val previewSizeText: String = "1280 × 720",
    val previewSizes: List<PreviewOption> = emptyList(),
    val selectedFrameRate: Int = DEFAULT_FRAME_RATE,
    val captureFps: Int = 0,
    val isPreviewing: Boolean = false,
    val isRecording: Boolean = false,
    val recordingSeconds: Long = 0L,
    val errorMessage: String? = null,
    val diagnosticText: String = "应用层：等待初始化",
    val recordingFiles: List<RecordingFile> = emptyList()
)

@Composable
private fun UsbCaptureTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFF36D399),
            secondary = Color(0xFF38BDF8),
            tertiary = Color(0xFFF87171),
            surface = Color(0xFF10151F),
            surfaceVariant = Color(0xFF1E293B),
            background = Color(0xFF070A0F),
            onPrimary = Color(0xFF052E1B),
            onSecondary = Color(0xFF082F49),
            onSurface = Color(0xFFE5EEF8)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF007A5A),
            secondary = Color(0xFF0369A1),
            tertiary = Color(0xFFDC2626),
            surface = Color(0xFFF8FAFC),
            surfaceVariant = Color(0xFFE2E8F0),
            background = Color(0xFFFFFFFF),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onSurface = Color(0xFF111827)
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun UsbCaptureScreen(
    uiState: UsbCameraUiState,
    themeMode: ThemeMode,
    screenOrientation: ScreenOrientation,
    controlsVisible: Boolean,
    functionMenuExpanded: Boolean,
    showSettings: Boolean,
    screenLocked: Boolean,
    singleFingerPanEnabled: Boolean,
    controlsInteractionTick: Int,
    onThemeModeChange: (ThemeMode) -> Unit,
    onRecordClick: () -> Unit,
    onResolutionSelected: (PreviewOption) -> Unit,
    onAutoDetectResolutions: () -> Unit,
    onFrameRateSelected: (Int) -> Unit,
    onFunctionMenuExpandedChange: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onSettingsDismiss: () -> Unit,
    onExitClick: () -> Unit,
    onPreviewTap: () -> Unit,
    onPreviewTransform: (Float, Float, Float, Float, Float) -> Unit,
    onAutoHideControls: () -> Unit,
    onToggleScreenLock: () -> Unit
) {
    val currentSingleFingerPanEnabled = rememberUpdatedState(singleFingerPanEnabled)
    val currentOnPreviewTransform = rememberUpdatedState(onPreviewTransform)
    LaunchedEffect(
        uiState.isPreviewing,
        controlsVisible,
        functionMenuExpanded,
        showSettings,
        screenLocked,
        controlsInteractionTick
    ) {
        if (uiState.isPreviewing && controlsVisible && !functionMenuExpanded && !showSettings && !screenLocked) {
            delay(CONTROLS_AUTO_HIDE_MS)
            onAutoHideControls()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PreviewShade(uiState, controlsVisible)
        if (uiState.isPreviewing) {
            val tapInteractionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .previewTransformGestures(
                        enabled = !screenLocked,
                        singleFingerPanEnabled = { currentSingleFingerPanEnabled.value },
                        onTransform = { zoom, panX, panY, centroidX, centroidY ->
                            currentOnPreviewTransform.value(
                                zoom,
                                panX,
                                panY,
                                centroidX,
                                centroidY
                            )
                        }
                    )
                    .clickable(
                        interactionSource = tapInteractionSource,
                        indication = null,
                        onClick = onPreviewTap
                    )
            )
        }
        CenterSignalState(uiState)
        CaptureFpsOverlay(uiState)
        AnimatedVisibility(
            visible = (controlsVisible || !uiState.isPreviewing) && !screenLocked,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160))
        ) {
            FunctionOverlay(
                uiState = uiState,
                functionMenuExpanded = functionMenuExpanded,
                onFunctionMenuExpandedChange = onFunctionMenuExpandedChange,
                onRecordClick = onRecordClick,
                onSettingsClick = onSettingsClick,
                onExitClick = onExitClick
            )
        }
        LockToggleOverlay(
            visible = uiState.isPreviewing,
            locked = screenLocked,
            onToggle = onToggleScreenLock
        )
        SettingsPanel(
            visible = showSettings,
            uiState = uiState,
            screenOrientation = screenOrientation,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onResolutionSelected = onResolutionSelected,
            onAutoDetectResolutions = onAutoDetectResolutions,
            onFrameRateSelected = onFrameRateSelected,
            onDismiss = onSettingsDismiss
        )
    }
}

private fun Modifier.previewTransformGestures(
    enabled: Boolean,
    singleFingerPanEnabled: () -> Boolean,
    onTransform: (Float, Float, Float, Float, Float) -> Unit
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val touchSlop = viewConfiguration.touchSlop
        val touchSlopSquared = touchSlop * touchSlop
        var accumulatedPanX = 0f
        var accumulatedPanY = 0f
        var singleFingerPanActive = false
        var hadMultiTouch = false
        var hasPressedPointers: Boolean
        do {
            val event = awaitPointerEvent()
            hasPressedPointers = event.changes.any { it.pressed }
            val pressedChanges = event.changes.filter { it.pressed }
            when {
                pressedChanges.size >= 2 -> {
                    hadMultiTouch = true
                    singleFingerPanActive = false
                    accumulatedPanX = 0f
                    accumulatedPanY = 0f
                    val zoomChange = event.calculateZoom()
                    val panChange = event.calculatePan()
                    val centroid = event.calculateCentroid()
                    onTransform(
                        zoomChange,
                        panChange.x,
                        panChange.y,
                        centroid.x,
                        centroid.y
                    )
                    event.changes.forEach { it.consume() }
                }

                pressedChanges.size == 1 && singleFingerPanEnabled() -> {
                    val change = pressedChanges.first()
                    val deltaX = change.position.x - change.previousPosition.x
                    val deltaY = change.position.y - change.previousPosition.y
                    if (hadMultiTouch) {
                        singleFingerPanActive = true
                    }

                    var panX = deltaX
                    var panY = deltaY
                    if (!singleFingerPanActive) {
                        accumulatedPanX += deltaX
                        accumulatedPanY += deltaY
                        val distanceSquared = accumulatedPanX * accumulatedPanX +
                            accumulatedPanY * accumulatedPanY
                        if (distanceSquared >= touchSlopSquared) {
                            singleFingerPanActive = true
                            panX = accumulatedPanX
                            panY = accumulatedPanY
                        }
                    }

                    if (singleFingerPanActive && (panX != 0f || panY != 0f)) {
                        onTransform(
                            1f,
                            panX,
                            panY,
                            change.position.x,
                            change.position.y
                        )
                        change.consume()
                    }
                }

                else -> {
                    singleFingerPanActive = false
                    accumulatedPanX = 0f
                    accumulatedPanY = 0f
                }
            }
        } while (hasPressedPointers)
    }
}

@Composable
private fun FunctionOverlay(
    uiState: UsbCameraUiState,
    functionMenuExpanded: Boolean,
    onFunctionMenuExpandedChange: (Boolean) -> Unit,
    onRecordClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onExitClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        contentAlignment = Alignment.TopStart
    ) {
        FunctionMenuButton(
            uiState = uiState,
            expanded = functionMenuExpanded,
            onExpandedChange = onFunctionMenuExpandedChange,
            onRecordClick = onRecordClick,
            onSettingsClick = onSettingsClick,
            onExitClick = onExitClick
        )
    }
}

@Composable
private fun FunctionMenuButton(
    uiState: UsbCameraUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onRecordClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onExitClick: () -> Unit
) {
    val buttonText = "功能"
    val targetColor = when (uiState.connectionState) {
        DeviceConnectionState.Connected -> MaterialTheme.colorScheme.primary
        DeviceConnectionState.Connecting -> MaterialTheme.colorScheme.secondary
        DeviceConnectionState.Error -> MaterialTheme.colorScheme.tertiary
        DeviceConnectionState.Waiting -> MaterialTheme.colorScheme.surfaceVariant
    }
    val color by animateColorAsState(targetColor, label = "function-status-color")
    Box {
        FilledTonalButton(
            onClick = { onExpandedChange(true) },
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
            )
        ) {
            ConnectionDot(uiState.connectionState, color)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Usb, contentDescription = buttonText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(buttonText, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "展开", modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text(if (uiState.isRecording) "停止录制" else "录制") },
                leadingIcon = {
                    Icon(
                        imageVector = if (uiState.isRecording) Icons.Rounded.Stop else Icons.Rounded.Videocam,
                        contentDescription = null
                    )
                },
                enabled = uiState.isPreviewing,
                onClick = {
                    onExpandedChange(false)
                    onRecordClick()
                }
            )
            DropdownMenuItem(
                text = { Text("设置采集分辨率/帧速率") },
                leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                onClick = {
                    onExpandedChange(false)
                    onSettingsClick()
                }
            )
            DropdownMenuItem(
                text = { Text("退出采集画面") },
                leadingIcon = { Icon(Icons.Rounded.Stop, contentDescription = null) },
                onClick = {
                    onExpandedChange(false)
                    onExitClick()
                }
            )
        }
    }
}

@Composable
private fun LockToggleOverlay(
    visible: Boolean,
    locked: Boolean,
    onToggle: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(160))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 8.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            FilledTonalButton(
                onClick = onToggle,
                shape = CircleShape,
                contentPadding = PaddingValues(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (locked) 0.86f else 0.5f)
                )
            ) {
                Icon(
                    imageVector = if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = if (locked) "解除锁定" else "锁定屏幕",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun PreviewShade(uiState: UsbCameraUiState, controlsVisible: Boolean) {
    val overlayAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = when {
            !uiState.isPreviewing -> 0.74f
            controlsVisible -> 0.18f
            else -> 0f
        },
        animationSpec = tween(450),
        label = "preview-overlay-alpha"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = overlayAlpha),
                        Color.Black.copy(alpha = overlayAlpha * 0.42f),
                        Color.Black.copy(alpha = overlayAlpha)
                    )
                )
            )
    )
}

@Composable
private fun CaptureFpsOverlay(uiState: UsbCameraUiState) {
    AnimatedVisibility(
        visible = uiState.isPreviewing,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(160))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.42f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
            ) {
                Text(
                    text = "${uiState.captureFps} 帧/秒",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ConnectionDot(state: DeviceConnectionState, color: Color) {
    val transition = rememberInfiniteTransition(label = "connection-dot")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "connection-dot-alpha"
    )
    Box(
        modifier = Modifier
            .size(9.dp)
            .alpha(if (state == DeviceConnectionState.Waiting) 0.55f else alpha)
            .background(color, CircleShape)
    )
}

@Composable
private fun ThemeSwitcher(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
            )
        ) {
            Icon(themeIcon(themeMode), contentDescription = themeMode.label, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(themeMode.label, maxLines = 1)
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "展开", modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    leadingIcon = { Icon(themeIcon(mode), contentDescription = mode.label) },
                    onClick = {
                        expanded = false
                        onThemeModeChange(mode)
                    }
                )
            }
        }
    }
}

private fun themeIcon(mode: ThemeMode) = when (mode) {
    ThemeMode.System -> Icons.Rounded.BrightnessAuto
    ThemeMode.Light -> Icons.Rounded.LightMode
    ThemeMode.Dark -> Icons.Rounded.DarkMode
}

@Composable
private fun CenterSignalState(uiState: UsbCameraUiState) {
    val detailText = if (uiState.isPreviewing && uiState.errorMessage != null) {
        "${uiState.deviceName}  ${uiState.previewSizeText}"
    } else {
        null
    }
    AnimatedVisibility(
        visible = !uiState.isPreviewing || uiState.errorMessage != null,
        enter = fadeIn(tween(350)),
        exit = fadeOut(tween(250))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SignalHalo(uiState.connectionState)
            Spacer(Modifier.height(18.dp))
            Text(
                text = uiState.errorMessage ?: uiState.statusText,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (detailText != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = detailText,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun SignalHalo(state: DeviceConnectionState) {
    val transition = rememberInfiniteTransition(label = "signal-halo")
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "signal-halo-scale"
    )
    val tint = when (state) {
        DeviceConnectionState.Connected -> MaterialTheme.colorScheme.primary
        DeviceConnectionState.Connecting -> MaterialTheme.colorScheme.secondary
        DeviceConnectionState.Error -> MaterialTheme.colorScheme.tertiary
        DeviceConnectionState.Waiting -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = Modifier
            .size(92.dp)
            .scale(scale)
            .border(1.dp, tint.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .background(tint.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (state == DeviceConnectionState.Connected) Icons.Rounded.Videocam else Icons.Rounded.Usb,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@Composable
private fun SettingsPanel(
    visible: Boolean,
    uiState: UsbCameraUiState,
    screenOrientation: ScreenOrientation,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onResolutionSelected: (PreviewOption) -> Unit,
    onAutoDetectResolutions: () -> Unit,
    onFrameRateSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(220)) { it / 2 } + fadeOut(tween(180))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 92.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "采集设置",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        )
                        TextButton(onClick = onDismiss) {
                            Text("完成")
                        }
                    }
                    InfoRow("设备", uiState.deviceName)
                    InfoRow("预览", uiState.previewSizeText)
                    InfoRow("方向", screenOrientation.label)
                    InfoRow("状态", uiState.statusText)
                    InfoRow("诊断", uiState.diagnosticText)
                    ResolutionSelector(
                        options = uiState.previewSizes,
                        current = uiState.previewSizeText,
                        onResolutionSelected = onResolutionSelected,
                        onAutoDetectResolutions = onAutoDetectResolutions
                    )
                    FrameRateSelector(
                        options = FRAME_RATE_OPTIONS,
                        current = uiState.selectedFrameRate,
                        onFrameRateSelected = onFrameRateSelected
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("主题", color = MaterialTheme.colorScheme.onSurface)
                        ThemeSwitcher(themeMode, onThemeModeChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun FrameRateSelector(
    options: List<Int>,
    current: Int,
    onFrameRateSelected: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "帧速率",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            fontSize = 13.sp
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { frameRate ->
                val selected = frameRate == current
                OutlinedButton(
                    onClick = { onFrameRateSelected(frameRate) },
                    enabled = !selected,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("${frameRate}帧/秒", fontSize = 12.sp)
                }
            }
        }
        Text(
            text = "当前库未暴露采集卡真实帧率枚举，此处为录制帧率预设。",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ResolutionSelector(
    options: List<PreviewOption>,
    current: String,
    onResolutionSelected: (PreviewOption) -> Unit,
    onAutoDetectResolutions: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "分辨率",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            fontSize = 13.sp
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onAutoDetectResolutions,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("自动检测", fontSize = 12.sp)
            }
            options.forEach { option ->
                val selected = option.label == current
                OutlinedButton(
                    onClick = { onResolutionSelected(option) },
                    enabled = !selected,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(option.label, fontSize = 12.sp)
                }
            }
        }
        if (options.isEmpty()) {
            Text(
                text = "等待设备或点击自动检测",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun RecordingList(files: List<RecordingFile>, onRecordingClick: (RecordingFile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "录制文件",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            fontSize = 13.sp
        )
        if (files.isEmpty()) {
            Text(
                text = "暂无文件",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                fontSize = 13.sp
            )
            return@Column
        }
        files.take(4).forEach { file ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.displayName,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatFileSize(file.sizeBytes),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                            fontSize = 12.sp
                        )
                    }
                    TextButton(onClick = { onRecordingClick(file) }) {
                        Text("播放")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f), fontSize = 13.sp)
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return "%02d:%02d".format(minutes, rest)
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 千字节"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "%.1f 千字节".format(kb)
    return "%.1f 兆字节".format(kb / 1024.0)
}
