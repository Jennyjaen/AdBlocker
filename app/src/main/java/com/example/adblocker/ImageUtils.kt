package com.example.adblocker

// #4. Screenshot — ImageUtils (entire implementation commented out; uncomment to enable step 4)
/*
import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtils {

    private const val JPEG_QUALITY = 85
    private const val MAX_LONG_EDGE = 1280
    private const val DEFAULT_CORNER_FRACTION = 0.3f

    fun toJpegBytes(bitmap: Bitmap): ByteArray {
        val scaled = scaleDownIfNeeded(bitmap)
        val shouldRecycleScaled = scaled !== bitmap
        try {
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            return stream.toByteArray()
        } finally {
            if (shouldRecycleScaled) {
                scaled.recycle()
            }
        }
    }

    fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun toJpegBase64(bitmap: Bitmap): String = toBase64(toJpegBytes(bitmap))

    fun cropCorner(
        bitmap: Bitmap,
        right: Boolean,
        bottom: Boolean,
        fraction: Float = DEFAULT_CORNER_FRACTION,
    ): Bitmap {
        val cropWidth = (bitmap.width * fraction).toInt().coerceIn(1, bitmap.width)
        val cropHeight = (bitmap.height * fraction).toInt().coerceIn(1, bitmap.height)
        val x = if (right) bitmap.width - cropWidth else 0
        val y = if (bottom) bitmap.height - cropHeight else 0
        return Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight)
    }

    fun cropAllCorners(bitmap: Bitmap, fraction: Float = DEFAULT_CORNER_FRACTION): List<Bitmap> {
        return listOf(
            cropCorner(bitmap, right = false, bottom = false, fraction = fraction),
            cropCorner(bitmap, right = true, bottom = false, fraction = fraction),
            cropCorner(bitmap, right = false, bottom = true, fraction = fraction),
            cropCorner(bitmap, right = true, bottom = true, fraction = fraction),
        )
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= MAX_LONG_EDGE) {
            return bitmap
        }
        val scale = MAX_LONG_EDGE.toFloat() / longEdge
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
*/
