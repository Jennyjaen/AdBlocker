package com.example.adblocker

// #4.5 GameSession — GameplaySessionStore (entire implementation commented out; uncomment with step 4.5+)
/*
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import java.io.File
import java.io.IOException

class GameplaySessionStore(private val context: Context) {

    private var sessionOwnerPackage: String? = null
    private var ownerLastSeenAtMs: Long = 0L
    private var buttonPressCountInSession: Int = 0
    private var lastReferenceSavedAtMs: Long = 0L
    private val referenceFiles = ArrayDeque<File>()

    // 기기마다 런처 패키지가 다르므로 목록 대신 OS에 물어본다.
    private val homePackage: String? by lazy {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager
            .resolveActivity(homeIntent, 0)
            ?.activityInfo
            ?.packageName
    }

    private val imePackages: Set<String> by lazy {
        val inputMethodManager =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        inputMethodManager?.enabledInputMethodList
            ?.map { inputMethod -> inputMethod.packageName }
            ?.toSet()
            .orEmpty()
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }
        val packageName = event.packageName?.toString() ?: return
        onForegroundPackageChanged(packageName)
    }

    fun onForegroundPackageChanged(packageName: String) {
        if (packageName == context.packageName) {
            return
        }

        // 알림창·볼륨 팝업·키보드는 「앱 전환」이 아니다.
        // 이 필터가 없으면 게임 중 알림창만 내려도 세션(참조 이미지)이 지워진다.
        if (isSystemOverlayPackage(packageName)) {
            return
        }

        expireSessionIfTimedOut()

        val owner = sessionOwnerPackage
        if (owner == null) {
            startSessionIfEligible(packageName)
            return
        }

        when {
            packageName == owner -> ownerLastSeenAtMs = System.currentTimeMillis()

            // Ad redirect target: keep the session so refs survive a back-press return.
            isTransientPackage(packageName) -> Unit

            else -> {
                clearSession(owner)
                startSessionIfEligible(packageName)
            }
        }
    }

    fun onAccessibilityButtonPressed(foregroundPackage: String?) {
        expireSessionIfTimedOut()

        if (foregroundPackage != null && sessionOwnerPackage == null) {
            startSessionIfEligible(foregroundPackage)
        }

        if (sessionOwnerPackage == null) {
            return
        }

        buttonPressCountInSession++
        ownerLastSeenAtMs = System.currentTimeMillis()
    }

    // 세션이 아예 없는 화면(홈 등)은 워밍업이 아니다.
    // 구분하지 않으면 홈에서 누를 때마다 「플레이 화면에서 한 번 더」만 반복된다.
    fun isWarmupButtonPress(): Boolean =
        sessionOwnerPackage != null && buttonPressCountInSession <= 1

    fun referencesFor(packageName: String?): List<File> {
        val owner = sessionOwnerPackage ?: return emptyList()
        if (packageName != null && packageName != owner) {
            return emptyList()
        }
        if (referenceFiles.size < MIN_REFERENCES_FOR_CLASSIFY) {
            return emptyList()
        }
        return referenceFiles.toList()
    }

    fun referenceCount(): Int = referenceFiles.size

    fun maybeAddReference(packageName: String?, jpegBytes: ByteArray) {
        val owner = sessionOwnerPackage ?: return
        if (packageName != null && packageName != owner) {
            return
        }
        if (buttonPressCountInSession <= 1) {
            return
        }

        val now = System.currentTimeMillis()
        if (lastReferenceSavedAtMs != 0L && now - lastReferenceSavedAtMs < MIN_REFERENCE_INTERVAL_MS) {
            return
        }

        try {
            val sessionDir = File(sessionsRoot(), owner)
            if (!sessionDir.exists() && !sessionDir.mkdirs()) {
                return
            }
            val file = File(sessionDir, "ref_$now.jpg")
            file.writeBytes(jpegBytes)

            referenceFiles.addLast(file)
            while (referenceFiles.size > MAX_REFERENCES) {
                referenceFiles.removeFirst().delete()
            }
            lastReferenceSavedAtMs = now
            Log.i(TAG, "session_refs=${referenceFiles.size} owner=$owner")
        } catch (error: IOException) {
            Log.w(TAG, "Failed to store gameplay reference", error)
        }
    }

    fun getSessionOwnerPackage(): String? = sessionOwnerPackage

    fun clearSession(packageName: String) {
        if (sessionOwnerPackage == packageName) {
            resetSessionState()
        }
        File(sessionsRoot(), packageName).deleteRecursively()
        Log.i(TAG, "session cleared for $packageName")
    }

    fun clearAll() {
        resetSessionState()
        sessionsRoot().deleteRecursively()
        Log.i(TAG, "all sessions cleared")
    }

    private fun startSessionIfEligible(packageName: String) {
        if (isHomeScreenPackage(packageName) ||
            isTransientPackage(packageName) ||
            isSystemOverlayPackage(packageName)
        ) {
            return
        }
        sessionOwnerPackage = packageName
        ownerLastSeenAtMs = System.currentTimeMillis()
        buttonPressCountInSession = 0
        lastReferenceSavedAtMs = 0L
        loadReferenceFilesForOwner(packageName)
        Log.i(TAG, "session started for $packageName refs=${referenceFiles.size}")
    }

    private fun resetSessionState() {
        sessionOwnerPackage = null
        ownerLastSeenAtMs = 0L
        buttonPressCountInSession = 0
        lastReferenceSavedAtMs = 0L
        referenceFiles.clear()
    }

    private fun loadReferenceFilesForOwner(packageName: String) {
        referenceFiles.clear()
        val sessionDir = File(sessionsRoot(), packageName)
        if (!sessionDir.isDirectory) {
            return
        }
        sessionDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("ref_") }
            ?.sortedBy { it.name }
            ?.forEach { referenceFiles.addLast(it) }
        while (referenceFiles.size > MAX_REFERENCES) {
            referenceFiles.removeFirst().delete()
        }
    }

    private fun expireSessionIfTimedOut() {
        val owner = sessionOwnerPackage ?: return
        if (ownerLastSeenAtMs == 0L) {
            return
        }
        if (System.currentTimeMillis() - ownerLastSeenAtMs > SESSION_TIMEOUT_MS) {
            clearSession(owner)
        }
    }

    private fun isHomeScreenPackage(packageName: String): Boolean =
        packageName == homePackage || isLauncherPackage(packageName)

    private fun isSystemOverlayPackage(packageName: String): Boolean =
        packageName in SYSTEM_OVERLAY_PACKAGES || packageName in imePackages

    private fun sessionsRoot(): File = File(context.cacheDir, SESSIONS_DIR_NAME)

    companion object {
        const val TAG = "AdBlockerGameSession"
        const val SESSIONS_DIR_NAME = "gameplay_sessions"

        private const val MAX_REFERENCES = 2
        private const val MIN_REFERENCES_FOR_CLASSIFY = 2
        private const val MIN_REFERENCE_INTERVAL_MS = 4_000L
        private const val SESSION_TIMEOUT_MS = 30L * 60L * 1000L

        // Ad landing targets: leaving the game for these should NOT drop the session.
        val TRANSIENT_PACKAGES = setOf(
            "com.android.chrome",
            "com.android.vending",
            "com.alibaba.aliexpress",
            "com.sec.android.app.sbrowser",
            "com.google.android.googlequicksearchbox",
        )

        // 앱 전환이 아닌 시스템 창들. 여기에 걸리면 세션을 유지도, 삭제도 하지 않는다.
        val SYSTEM_OVERLAY_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
        )

        val LAUNCHER_PACKAGES = setOf(
            "com.sec.android.app.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher",
            "com.android.launcher3",
            "com.miui.home",
            "com.huawei.android.launcher",
        )

        fun isTransientPackage(packageName: String): Boolean = packageName in TRANSIENT_PACKAGES

        fun isLauncherPackage(packageName: String): Boolean = packageName in LAUNCHER_PACKAGES
    }
}
*/
