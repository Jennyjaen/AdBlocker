package com.example.adblocker

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.Locale

class AdBlockerAccessibilityService : AccessibilityService(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var accessibilityButtonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        Log.i(TAG, "Service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected — waiting for accessibility button")

        val callback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(controller: AccessibilityButtonController) {
                onAccessibilityButtonPressed()
            }
        }
        accessibilityButtonCallback = callback
        accessibilityButtonController.registerAccessibilityButtonCallback(
            callback,
            Handler(Looper.getMainLooper()),
        )
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale.KOREAN
            pendingSpeech?.let { message ->
                pendingSpeech = null
                speakNow(message)
            }
            Log.i(TAG, "TTS ready")
        } else {
            Log.e(TAG, "TTS init failed: status=$status")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty: analysis runs only on accessibility button press.
    }

    override fun onInterrupt() {
        // No long-running feedback to interrupt yet.
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed")
        accessibilityButtonCallback?.let { callback ->
            accessibilityButtonController.unregisterAccessibilityButtonCallback(callback)
        }
        accessibilityButtonCallback = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        pendingSpeech = null
        super.onDestroy()
    }

    private fun onAccessibilityButtonPressed() {
        Log.i(TAG, LOG_ACCESSIBILITY_BUTTON_CLICKED)
        speak(getString(R.string.tts_accessibility_button_ok))
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            pendingSpeech = text
            Log.i(TAG, "TTS not ready yet; queued speech")
            return
        }
        speakNow(text)
    }

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
