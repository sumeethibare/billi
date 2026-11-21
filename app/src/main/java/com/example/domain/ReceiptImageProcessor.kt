package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Compresses receipt photos to crisp, receipt-legible JPEGs that fit safely within
 * Firestore's document size limits (<= 550 KB Base64) while maintaining an app-private
 * offline disk cache for instant viewing and archival PDF generation.
 */
object ReceiptImageProcessor {

    private const val MAX_DIMENSION_PX = 1080
    private const val MAX_BASE64_CHARS = 540_000

    private fun receiptsDir(context: Context): File =
        File(context.filesDir, "receipts").apply { if (!exists()) mkdirs() }

    fun localReceiptFile(context: Context, billId: String): File =
        File(receiptsDir(context), "$billId.jpg")

    suspend fun processUriToCompressedBase64(context: Context, uri: Uri): Pair<Bitmap, String>? =
        withContext(Dispatchers.IO) {
            try {
                val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

                var sampleSize = 1
                while (
                    bounds.outWidth / sampleSize > MAX_DIMENSION_PX * 2 ||
                    bounds.outHeight / sampleSize > MAX_DIMENSION_PX * 2
                ) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: return@withContext null

                val rotated = applyExifRotation(rawBitmap, orientation)
                if (rotated !== rawBitmap) rawBitmap.recycle()

                val scaled = scaleDownIfNeeded(rotated, MAX_DIMENSION_PX)
                if (scaled !== rotated) rotated.recycle()

                val base64 = compressBitmapToBase64(scaled) ?: return@withContext null
                scaled to base64
            } catch (_: Exception) {
                null
            }
        }

    fun compressBitmapToBase64(bitmap: Bitmap): String? {
        var working = bitmap
        var quality = 78
        var encoded = ""
        repeat(6) {
            val out = ByteArrayOutputStream()
            working.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (encoded.length <= MAX_BASE64_CHARS) {
                return encoded
            }
            if (quality > 46) {
                quality -= 12
            } else {
                val nextWidth = (working.width * 0.75f).toInt().coerceAtLeast(360)
                val nextHeight = (working.height * 0.75f).toInt().coerceAtLeast(360)
                working = Bitmap.createScaledBitmap(working, nextWidth, nextHeight, true)
            }
        }
        return if (encoded.length <= MAX_BASE64_CHARS) encoded else null
    }

    suspend fun saveBase64ToLocalCache(context: Context, billId: String, base64: String): File? =
        withContext(Dispatchers.IO) {
            try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val file = localReceiptFile(context, billId)
                FileOutputStream(file).use { it.write(bytes) }
                file
            } catch (_: Exception) {
                null
            }
        }

    suspend fun loadBitmapFromLocalCache(context: Context, billId: String): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val file = localReceiptFile(context, billId)
                if (!file.exists()) return@withContext null
                BitmapFactory.decodeFile(file.absolutePath)
            } catch (_: Exception) {
                null
            }
        }

    fun decodeBase64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    fun deleteLocalCache(context: Context, billId: String) {
        try {
            localReceiptFile(context, billId).delete()
        } catch (_: Exception) {
        }
    }

    private fun applyExifRotation(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val maxCurrent = maxOf(bitmap.width, bitmap.height)
        if (maxCurrent <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxCurrent.toFloat()
        val newW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val newH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}
