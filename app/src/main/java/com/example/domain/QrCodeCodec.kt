package com.example.domain

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

data class TripQrInvite(
    val tripId: String,
    val joinCode: String
)

object QrCodeCodec {

    fun encodeInviteUri(tripId: String, joinCode: String): String =
        "billi://trip/$tripId?code=$joinCode"

    fun encodeCompactManualCode(tripId: String, joinCode: String): String =
        "$tripId:$joinCode"

    /**
     * Parses either a scanned QR URI (`billi://trip/<tripId>?code=<joinCode>`) or a manual
     * copy-pasted invite token (`<tripId>:<joinCode>`).
     */
    fun parseInvitePayload(raw: String): TripQrInvite? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("billi://", ignoreCase = true)) {
            val uri = try {
                Uri.parse(trimmed)
            } catch (_: Exception) {
                return null
            }
            val tripId = uri.lastPathSegment?.trim().orEmpty()
            val code = uri.getQueryParameter("code")?.trim()?.uppercase().orEmpty()
            return if (isValidToken(tripId) && isValidJoinCode(code)) {
                TripQrInvite(tripId = tripId, joinCode = code)
            } else {
                null
            }
        }

        val colonIndex = trimmed.indexOf(':')
        if (colonIndex > 0 && colonIndex < trimmed.lastIndex) {
            val tripId = trimmed.substring(0, colonIndex).trim()
            val code = trimmed.substring(colonIndex + 1).trim().uppercase()
            return if (isValidToken(tripId) && isValidJoinCode(code)) {
                TripQrInvite(tripId = tripId, joinCode = code)
            } else {
                null
            }
        }

        return null
    }

    private fun isValidToken(value: String): Boolean =
        value.length in 1..128 && value.matches(Regex("^[a-zA-Z0-9_\\-]+$"))

    private fun isValidJoinCode(value: String): Boolean =
        value.length in 6..20 && value.matches(Regex("^[A-Z0-9]+$"))

    fun generateQrBitmap(
        content: String,
        sizePx: Int = 640,
        foregroundColor: Int = Color.parseColor("#1B4D3E"),
        backgroundColor: Int = Color.WHITE
    ): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val pixels = IntArray(sizePx * sizePx)
            for (y in 0 until sizePx) {
                val offset = y * sizePx
                for (x in 0 until sizePx) {
                    pixels[offset + x] = if (matrix.get(x, y)) foregroundColor else backgroundColor
                }
            }
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
            }
        } catch (_: Exception) {
            null
        }
    }

    private val qrReader: MultiFormatReader by lazy {
        MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true
                )
            )
        }
    }

    fun decodeFromImageProxy(imageProxy: ImageProxy): String? {
        val plane = imageProxy.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = plane.rowStride

        // Copy packed Y luminance plane if rowStride > width
        val yData = if (rowStride == width) {
            data
        } else {
            ByteArray(width * height).also { packed ->
                for (row in 0 until height) {
                    val srcPos = row * rowStride
                    val len = minOf(width, data.size - srcPos)
                    if (len > 0) {
                        System.arraycopy(data, srcPos, packed, row * width, len)
                    }
                }
            }
        }

        val source = PlanarYUVLuminanceSource(
            yData,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            qrReader.decodeWithState(binaryBitmap).text
        } catch (_: Exception) {
            null
        } finally {
            qrReader.reset()
        }
    }
}
