package com.example.adblocker

// #7 run — SpokenMessageBuilder (entire implementation commented out; uncomment with step 7+)
/*
import android.content.Context

class SpokenMessageBuilder(private val context: Context) {

    fun analyzing(): String = context.getString(R.string.tts_analyzing)

    fun notAd(): String = context.getString(R.string.tts_not_ad)

    fun captureFailed(): String = context.getString(R.string.tts_capture_failed)

    fun apiKeyMissing(): String = context.getString(R.string.tts_api_key_missing)

    fun networkError(): String = context.getString(R.string.tts_network_error)

    fun analysisFailed(): String = context.getString(R.string.tts_analysis_failed)

    fun sessionWarmupHint(): String = context.getString(R.string.tts_session_warmup_hint)

    fun adControls(result: AdControlsResult): String {
        val intro = context.getString(R.string.tts_ad_intro)
        val close = controlPhrase(
            label = context.getString(R.string.tts_label_close_button),
            quadrant = result.closeButton,
        )
        val skip = controlPhrase(
            label = context.getString(R.string.tts_label_skip),
            quadrant = result.skipIndicator,
        )
        return "$intro $close $skip"
    }

    private fun controlPhrase(label: String, quadrant: ScreenQuadrant): String {
        return if (quadrant == ScreenQuadrant.NOT_FOUND) {
            context.getString(R.string.tts_control_not_found, label)
        } else {
            context.getString(R.string.tts_control_at, label, quadrantLabel(quadrant))
        }
    }

    private fun quadrantLabel(quadrant: ScreenQuadrant): String {
        val resId = when (quadrant) {
            ScreenQuadrant.TOP_LEFT -> R.string.tts_quadrant_top_left
            ScreenQuadrant.TOP_RIGHT -> R.string.tts_quadrant_top_right
            ScreenQuadrant.BOTTOM_LEFT -> R.string.tts_quadrant_bottom_left
            ScreenQuadrant.BOTTOM_RIGHT -> R.string.tts_quadrant_bottom_right
            ScreenQuadrant.NOT_FOUND -> R.string.tts_quadrant_unknown
        }
        return context.getString(resId)
    }
}
*/
