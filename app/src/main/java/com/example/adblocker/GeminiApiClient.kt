package com.example.adblocker

// #6 LLM API — GeminiApiClient (entire implementation commented out; uncomment with step 6+)
/*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiApiClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {

    suspend fun classifyAdScreen(
        currentJpegB64: String,
        referenceJpegB64s: List<String> = emptyList(),
    ): Boolean = withContext(Dispatchers.IO) {
        val apiKey = requireApiKey()
        val useReferences = referenceJpegB64s.size >= MIN_REFERENCES
        val prompt = if (useReferences) {
            GeminiPrompts.CLASSIFY_WITH_REFERENCES_PROMPT
        } else {
            GeminiPrompts.CLASSIFY_PROMPT
        }

        val imageParts = buildList {
            if (useReferences) {
                referenceJpegB64s.take(MIN_REFERENCES).forEach { referenceJpegB64 ->
                    add(textPart(REFERENCE_LABEL))
                    add(jpegInlinePart(referenceJpegB64))
                }
            }
            add(textPart(CURRENT_SCREEN_LABEL))
            add(jpegInlinePart(currentJpegB64))
        }

        val schema = JSONObject()
            .put("type", "OBJECT")
            .put("properties", JSONObject().put("is_ad", JSONObject().put("type", "BOOLEAN")))
            .put("required", JSONArray().put("is_ad"))

        val json = postGenerateContent(apiKey, prompt, imageParts, schema)
        readBoolean(json, "is_ad")
    }

    suspend fun locateAdControls(
        fullJpegB64: String,
        cornerJpegB64s: List<String>,
    ): AdControlsResult = withContext(Dispatchers.IO) {
        val apiKey = requireApiKey()

        // 라벨 없이 이미지만 넣으면 모델이 몇 번째 이미지가 어느 모서리인지 알 수 없다.
        // 순서는 ImageUtils.cropAllCorners()와 같아야 한다.
        val imageParts = buildList {
            add(textPart(FULL_SCREEN_LABEL))
            add(jpegInlinePart(fullJpegB64))
            cornerJpegB64s.take(MAX_CORNERS).forEachIndexed { index, cornerJpegB64 ->
                add(textPart(CORNER_LABELS[index]))
                add(jpegInlinePart(cornerJpegB64))
            }
        }

        val quadrantEnum = JSONArray()
        ScreenQuadrant.entries.forEach { quadrantEnum.put(it.schemaValue) }

        val schema = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject()
                    .put("close_button", JSONObject().put("type", "STRING").put("enum", quadrantEnum))
                    .put("skip_indicator", JSONObject().put("type", "STRING").put("enum", quadrantEnum)),
            )
            .put("required", JSONArray().put("close_button").put("skip_indicator"))

        val json = postGenerateContent(apiKey, GeminiPrompts.LOCATE_PROMPT, imageParts, schema)
        AdControlsResult(
            closeButton = ScreenQuadrant.fromSchemaValue(readString(json, "close_button")),
            skipIndicator = ScreenQuadrant.fromSchemaValue(readString(json, "skip_indicator")),
        )
    }

    private fun requireApiKey(): String {
        val key = BuildConfig.GEMINI_API_KEY.trim()
        if (key.isEmpty()) {
            throw GeminiApiError.ApiKeyMissing
        }
        return key
    }

    private fun postGenerateContent(
        apiKey: String,
        prompt: String,
        imageParts: List<JSONObject>,
        schema: JSONObject,
    ): JSONObject {
        val url = "$API_BASE_URL${GeminiConfig.MODEL_ID}:generateContent"

        val parts = JSONArray().put(JSONObject().put("text", prompt))
        imageParts.forEach { parts.put(it) }

        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0)
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", schema),
            )

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val rawBody = try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw GeminiApiError.HttpError(response.code, responseBody)
                }
                responseBody
            }
        } catch (error: IOException) {
            throw GeminiApiError.NetworkError(error)
        }

        return try {
            val text = JSONObject(rawBody)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            JSONObject(text)
        } catch (error: JSONException) {
            throw GeminiApiError.ParseError(error)
        }
    }

    private fun readBoolean(json: JSONObject, key: String): Boolean {
        return try {
            json.getBoolean(key)
        } catch (error: JSONException) {
            throw GeminiApiError.ParseError(error)
        }
    }

    private fun readString(json: JSONObject, key: String): String {
        return try {
            json.getString(key)
        } catch (error: JSONException) {
            throw GeminiApiError.ParseError(error)
        }
    }

    private fun textPart(text: String): JSONObject = JSONObject().put("text", text)

    private fun jpegInlinePart(jpegB64: String): JSONObject {
        return JSONObject().put(
            "inline_data",
            JSONObject()
                .put("mime_type", "image/jpeg")
                .put("data", jpegB64),
        )
    }

    companion object {
        const val TAG = "AdBlockerGemini"

        private const val API_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
        private const val MIN_REFERENCES = 2
        private const val MAX_CORNERS = 4

        private const val REFERENCE_LABEL = "image: reference gameplay screen (not an ad)"
        private const val CURRENT_SCREEN_LABEL = "image: current screen to classify"
        private const val FULL_SCREEN_LABEL = "image: full screen"
        private val CORNER_LABELS = listOf(
            "image: top-left corner crop",
            "image: top-right corner crop",
            "image: bottom-left corner crop",
            "image: bottom-right corner crop",
        )

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(45, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build()
        }
    }
}
*/
