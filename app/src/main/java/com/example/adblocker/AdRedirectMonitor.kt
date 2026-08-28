package com.example.adblocker

// #4.5 GameSession — AdRedirectMonitor (entire implementation commented out; uncomment with step 4.5+)
/*
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AdRedirectMonitor(
    private val sessionStore: GameplaySessionStore,
    private val onRedirectDetected: () -> Unit,
) {

    private var redirectAnnouncedForEpisode = false

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return
        val owner = sessionStore.getSessionOwnerPackage() ?: return

        if (packageName == owner) {
            redirectAnnouncedForEpisode = false
            return
        }

        if (!GameplaySessionStore.isTransientPackage(packageName)) {
            return
        }

        if (redirectAnnouncedForEpisode) {
            return
        }

        redirectAnnouncedForEpisode = true
        Log.i(TAG, "ad_redirect_detected owner=$owner target=$packageName")
        onRedirectDetected()
    }

    fun reset() {
        redirectAnnouncedForEpisode = false
    }

    companion object {
        const val TAG = "AdBlockerRedirect"
    }
}
*/
