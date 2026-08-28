package com.example.adblocker

// #6 LLM API — data models (entire file commented out; uncomment with step 6+)
/*
enum class ScreenQuadrant(val schemaValue: String) {
    TOP_LEFT("top-left"),
    TOP_RIGHT("top-right"),
    BOTTOM_LEFT("bottom-left"),
    BOTTOM_RIGHT("bottom-right"),
    NOT_FOUND("not_found"),
    ;

    companion object {
        fun fromSchemaValue(value: String): ScreenQuadrant {
            return entries.firstOrNull { it.schemaValue == value } ?: NOT_FOUND
        }
    }
}

data class AdControlsResult(
    val closeButton: ScreenQuadrant,
    val skipIndicator: ScreenQuadrant,
)

data class PipelineResult(
    val packageName: String?,
    val navBarVisible: Boolean,
    val referenceCount: Int,
    val isAd: Boolean,
    val controls: AdControlsResult?,
)

sealed class GeminiApiError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    class HttpError(val code: Int, val body: String) : GeminiApiError("HTTP $code: $body")

    class ParseError(cause: Throwable) : GeminiApiError("Invalid response payload", cause)

    class NetworkError(cause: Throwable) : GeminiApiError("Network failure", cause)

    data object ApiKeyMissing : GeminiApiError("API key missing")
}
*/
