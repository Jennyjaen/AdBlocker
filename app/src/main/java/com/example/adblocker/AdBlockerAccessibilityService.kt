package com.example.adblocker

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.Locale

// #4. Screenshot — imports (commented out; uncomment with step 4+)
// import android.graphics.Bitmap
// import android.view.Display

// #7 run — imports (commented out; uncomment with step 7+)
// import android.os.SystemClock
// import kotlinx.coroutines.suspendCancellableCoroutine
// import kotlin.coroutines.resume
// import kotlinx.coroutines.delay
// import kotlinx.coroutines.withContext

// #8 cancel — imports (commented out; uncomment with step 8+)
// import kotlinx.coroutines.CoroutineScope
// import kotlinx.coroutines.Dispatchers
// import kotlinx.coroutines.Job
// import kotlinx.coroutines.SupervisorJob
// import kotlinx.coroutines.cancel
// import kotlinx.coroutines.launch

class AdBlockerAccessibilityService : AccessibilityService(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var accessibilityButtonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null

    // #8 cancel — one scope for the service, one in-flight analysis job (commented out)
    // private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // private var analysisJob: Job? = null

    // #4.5 GameSession — session store + redirect monitor (commented out)
    // private var gameplaySessionStore: GameplaySessionStore? = null
    // private var adRedirectMonitor: AdRedirectMonitor? = null

    // #6 LLM API — client (commented out)
    // private val geminiApiClient = GeminiApiClient()

    // #7 run — resource-backed Korean templates (commented out)
    // private var spokenMessages: SpokenMessageBuilder? = null

    // #4. Screenshot — takeScreenshot()은 333ms 안에 다시 부르면 실패하므로 직접 간격을 지킨다.
    // private var lastScreenshotAtMs = 0L

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)

        // #7 run — init templates (commented out)
        // spokenMessages = SpokenMessageBuilder(this)

        // 리다이렉트 문장은 strings.xml에서 직접 읽는다 (7단계 없이 4.5만 켜도 동작).
        // #4.5 GameSession — init store + monitor (commented out)
        // val store = GameplaySessionStore(this)
        // gameplaySessionStore = store
        // adRedirectMonitor = AdRedirectMonitor(store) {
        //     speak(getString(R.string.tts_ad_redirect_hint))
        // }

        Log.i(TAG, "Service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        // onServiceConnected는 재연결 시 다시 호출될 수 있다. 이전 콜백을 남기면 버튼 1탭에 2번 반응한다.
        accessibilityButtonCallback?.let { previous ->
            accessibilityButtonController.unregisterAccessibilityButtonCallback(previous)
        }

        val callback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(controller: AccessibilityButtonController) {
                onAccessibilityButtonPressed()
            }

            override fun onAvailabilityChanged(
                controller: AccessibilityButtonController,
                available: Boolean,
            ) {
                Log.i(TAG, "accessibility_button_available=$available")
            }
        }
        accessibilityButtonCallback = callback
        accessibilityButtonController.registerAccessibilityButtonCallback(
            callback,
            Handler(Looper.getMainLooper()),
        )

        Log.i(
            TAG,
            "Service connected — accessibility_button_available=" +
                "${accessibilityButtonController.isAccessibilityButtonAvailable}",
        )
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            ttsReady = false
            Log.e(TAG, "TTS init failed: status=$status")
            return
        }

        val languageResult = tts?.setLanguage(Locale.KOREAN) ?: TextToSpeech.ERROR
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Log.e(TAG, "Korean voice unavailable: languageResult=$languageResult")
        }

        ttsReady = true
        Log.i(TAG, "TTS ready (languageResult=$languageResult)")

        pendingSpeech?.let { message ->
            pendingSpeech = null
            speakNow(message)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No capture / classify here: analysis runs only on accessibility button press.
        // #4.5 GameSession — session keep/clear + ad-redirect TTS (commented out)
        // if (event != null) {
        //     gameplaySessionStore?.onAccessibilityEvent(event)
        //     adRedirectMonitor?.onAccessibilityEvent(event)
        // }
    }

    override fun onInterrupt() {
        // No long-running feedback to interrupt yet.
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed")

        // #8 cancel — stop in-flight analysis before tearing down (commented out)
        // analysisJob?.cancel()
        // analysisJob = null
        // serviceScope.cancel()

        accessibilityButtonCallback?.let { callback ->
            accessibilityButtonController.unregisterAccessibilityButtonCallback(callback)
        }
        accessibilityButtonCallback = null

        // #4.5 GameSession — drop every cached reference JPEG (commented out)
        // gameplaySessionStore?.clearAll()
        // gameplaySessionStore = null
        // adRedirectMonitor = null

        // #7 run — release templates (commented out)
        // spokenMessages = null

        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        pendingSpeech = null
        super.onDestroy()
    }

    private fun onAccessibilityButtonPressed() {
        Log.i(TAG, LOG_ACCESSIBILITY_BUTTON_CLICKED)

        // 활성화 시 아래 step-3 speak 줄과 단독 디버그 3줄을 모두 주석 처리해야 한다.
        // #7 run / #8 cancel — 전체 파이프라인 (commented out)
        // analysisJob?.cancel()
        // analysisJob = serviceScope.launch { runPipeline() }
        // return

        speak(getString(R.string.tts_accessibility_button_ok))

        // #5 NavBar — 단독 디버그 로그 (5단계 확인용; 7단계 활성화 시 다시 주석)
        // Log.i(TAG, "navBarVisible=${NavBarDetector.detectNavBarVisible(this)}")

        // #4.5 GameSession — 단독 디버그 로그 (4.5단계 확인용; 7단계 활성화 시 다시 주석)
        // gameplaySessionStore?.onAccessibilityButtonPressed(
        //     rootInActiveWindow?.packageName?.toString(),
        // )
        // Log.i(
        //     GameplaySessionStore.TAG,
        //     "session_owner=${gameplaySessionStore?.getSessionOwnerPackage()} " +
        //         "refs=${gameplaySessionStore?.referenceCount()}",
        // )

        // #4. Screenshot — 단독 디버그 캡처 (4단계 확인용; 7단계 활성화 시 다시 주석)
        // captureScreenshotDebug()
    }

    // #4. Screenshot — 단독 디버그 캡처 함수 (4단계 확인용; 7단계 활성화 시 다시 주석)
    /*
    private fun captureScreenshotDebug() {
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val hardwareBuffer = result.hardwareBuffer
                    val hardwareBitmap =
                        Bitmap.wrapHardwareBuffer(hardwareBuffer, result.colorSpace)
                    val softwareBitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBuffer.close()
                    hardwareBitmap?.recycle()

                    if (softwareBitmap == null) {
                        speak(getString(R.string.tts_capture_failed))
                        return
                    }

                    try {
                        val jpegBase64 = ImageUtils.toJpegBase64(softwareBitmap)
                        Log.i(TAG, "capture_ok base64_length=${jpegBase64.length}")
                        speak(getString(R.string.tts_capture_ok))
                    } finally {
                        softwareBitmap.recycle()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "Screenshot failed: errorCode=$errorCode")
                    speak(getString(R.string.tts_capture_failed))
                }
            },
        )
    }
    */

    // #7 run — full pipeline: session -> capture -> navbar -> classify -> locate -> TTS (commented out)
    /*
    private suspend fun runPipeline() {
        val store = gameplaySessionStore ?: return
        val messages = spokenMessages ?: return

        // 키가 없으면 캡처·세션 카운트를 소모하지 않고 바로 끝낸다.
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            speak(messages.apiKeyMissing())
            return
        }

        val foregroundPackage = rootInActiveWindow?.packageName?.toString()
        store.onAccessibilityButtonPressed(foregroundPackage)
        val warmupPress = store.isWarmupButtonPress()

        speak(messages.analyzing())

        // #5 NavBar — weak hint only; never decides is_ad on its own
        val navBarVisible = NavBarDetector.detectNavBarVisible(this)

        val bitmap = captureScreenshotBitmap()
        if (bitmap == null) {
            speak(messages.captureFailed())
            return
        }

        try {
            analyzeCapturedScreen(
                bitmap = bitmap,
                foregroundPackage = foregroundPackage,
                navBarVisible = navBarVisible,
                warmupPress = warmupPress,
                store = store,
                messages = messages,
            )
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun analyzeCapturedScreen(
        bitmap: Bitmap,
        foregroundPackage: String?,
        navBarVisible: Boolean,
        warmupPress: Boolean,
        store: GameplaySessionStore,
        messages: SpokenMessageBuilder,
    ) {
        val referenceFiles = store.referencesFor(foregroundPackage)

        // JPEG 압축·Base64·참조 파일 읽기는 전체화면 비트맵에서 수백 ms가 걸린다.
        // 접근성 서비스 메인 스레드를 막으면 시스템 전체의 접근성 이벤트가 밀린다.
        val encoded = withContext(Dispatchers.Default) {
            val jpegBytes = ImageUtils.toJpegBytes(bitmap)
            EncodedScreen(
                jpegBytes = jpegBytes,
                jpegB64 = ImageUtils.toBase64(jpegBytes),
                referenceB64s = referenceFiles.mapNotNull { file ->
                    runCatching { ImageUtils.toBase64(file.readBytes()) }.getOrNull()
                },
            )
        }

        try {
            val isAd = geminiApiClient.classifyAdScreen(
                currentJpegB64 = encoded.jpegB64,
                referenceJpegB64s = encoded.referenceB64s,
            )

            if (!isAd) {
                if (warmupPress) {
                    speak(messages.sessionWarmupHint())
                } else {
                    store.maybeAddReference(foregroundPackage, encoded.jpegBytes)
                    speak(messages.notAd())
                }
                logPipelineResult(foregroundPackage, navBarVisible, store, false, null)
                return
            }

            val controls = locateAdControls(bitmap, encoded.jpegB64)
            speak(messages.adControls(controls))
            logPipelineResult(foregroundPackage, navBarVisible, store, true, controls)
        } catch (error: GeminiApiError.ApiKeyMissing) {
            Log.w(GeminiApiClient.TAG, "API key missing")
            speak(messages.apiKeyMissing())
        } catch (error: GeminiApiError.NetworkError) {
            Log.e(GeminiApiClient.TAG, "Gemini network failure", error)
            speak(messages.networkError())
        } catch (error: GeminiApiError) {
            Log.e(GeminiApiClient.TAG, "Gemini call failed", error)
            speak(messages.analysisFailed())
        }
    }

    private suspend fun locateAdControls(
        bitmap: Bitmap,
        currentJpegB64: String,
    ): AdControlsResult {
        val cornerJpegB64s = withContext(Dispatchers.Default) {
            val corners = ImageUtils.cropAllCorners(bitmap)
            try {
                corners.map { corner -> ImageUtils.toJpegBase64(corner) }
            } finally {
                corners.forEach { corner ->
                    if (!corner.isRecycled) {
                        corner.recycle()
                    }
                }
            }
        }

        return geminiApiClient.locateAdControls(
            fullJpegB64 = currentJpegB64,
            cornerJpegB64s = cornerJpegB64s,
        )
    }

    private fun logPipelineResult(
        foregroundPackage: String?,
        navBarVisible: Boolean,
        store: GameplaySessionStore,
        isAd: Boolean,
        controls: AdControlsResult?,
    ) {
        val result = PipelineResult(
            packageName = foregroundPackage,
            navBarVisible = navBarVisible,
            referenceCount = store.referenceCount(),
            isAd = isAd,
            controls = controls,
        )
        Log.i(TAG, "pipeline_result=$result")
    }

    private suspend fun captureScreenshotBitmap(): Bitmap? {
        // 플랫폼은 마지막 요청으로부터 333ms 이내의 takeScreenshot을
        // ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT로 거절한다.
        // 연타 시 「캡처할 수 없습니다」로 오안내되지 않도록 우리가 먼저 간격을 맞춘다.
        val sinceLastMs = SystemClock.uptimeMillis() - lastScreenshotAtMs
        if (lastScreenshotAtMs != 0L && sinceLastMs < MIN_SCREENSHOT_INTERVAL_MS) {
            delay(MIN_SCREENSHOT_INTERVAL_MS - sinceLastMs)
        }
        lastScreenshotAtMs = SystemClock.uptimeMillis()
        return awaitScreenshot()
    }

    // #4. Screenshot — hardware buffer must be closed; hardware bitmaps cannot be compressed
    private suspend fun awaitScreenshot(): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val hardwareBuffer = result.hardwareBuffer
                        val hardwareBitmap =
                            Bitmap.wrapHardwareBuffer(hardwareBuffer, result.colorSpace)
                        val softwareBitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        hardwareBuffer.close()
                        hardwareBitmap?.recycle()

                        if (continuation.isActive) {
                            continuation.resume(softwareBitmap)
                        } else {
                            softwareBitmap?.recycle()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "Screenshot failed: errorCode=$errorCode")
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                },
            )
        }

    private class EncodedScreen(
        val jpegBytes: ByteArray,
        val jpegB64: String,
        val referenceB64s: List<String>,
    )

    private const val MIN_SCREENSHOT_INTERVAL_MS = 400L
    */

    private fun speak(text: String) {
        if (!ttsReady) {
            pendingSpeech = text
            Log.i(TAG, "TTS not ready yet; queued speech")
            return
        }
        speakNow(text)
    }

    // #8 cancel — QUEUE_FLUSH so the newest announcement wins on rapid button taps
    private fun speakNow(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ACCESSIBILITY_BUTTON)
        Log.i(TAG, "Speaking: $text")
    }

    companion object {
        const val TAG = "AdBlockerA11y"
        const val LOG_ACCESSIBILITY_BUTTON_CLICKED = "accessibility_button_clicked"

        private const val UTTERANCE_ACCESSIBILITY_BUTTON = "accessibility_button"
    }
}
