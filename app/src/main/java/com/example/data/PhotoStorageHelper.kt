package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

object PhotoStorageHelper {

    /**
     * Encodes a small cropped student portrait thumbnail (144x144 JPEG, ~6-12 KB)
     * as a compact `data:image/jpeg;base64,...` string for optional profile display.
     * Live camera video frames are NEVER uploaded to Firestore.
     */
    fun encodeThumbnailDataUri(bitmap: Bitmap, maxDim: Int = 144, quality: Int = 72): String {
        val w = max(1, bitmap.width)
        val h = max(1, bitmap.height)
        val scale = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h, 1f)
        val targetW = max(1, (w * scale).toInt())
        val targetH = max(1, (h * scale).toInt())
        val scaled = if (targetW != w || targetH != h) {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    fun decodeDataUriToBitmap(dataUri: String?): Bitmap? {
        if (dataUri.isNullOrBlank()) return null
        return try {
            val commaIdx = dataUri.indexOf(',')
            val rawBase64 = if (commaIdx >= 0) dataUri.substring(commaIdx + 1) else dataUri
            val bytes = Base64.decode(rawBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    fun decodeUriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            var sampleSize = 1
            while (opts.outWidth / sampleSize > 640 || opts.outHeight / sampleSize > 640) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Crops and aligns a detected face bounding box from a frame bitmap with slight margin,
     * applying optional horizontal mirror for front-facing camera portraits.
     */
    fun cropFaceRegion(
        source: Bitmap,
        boxLeft: Int,
        boxTop: Int,
        boxRight: Int,
        boxBottom: Int,
        marginFraction: Float = 0.12f,
        mirrorHorizontal: Boolean = false
    ): Bitmap {
        val boxW = max(1, boxRight - boxLeft)
        val boxH = max(1, boxBottom - boxTop)
        val padX = (boxW * marginFraction).toInt()
        val padY = (boxH * marginFraction).toInt()

        val left = (boxLeft - padX).coerceIn(0, max(0, source.width - 1))
        val top = (boxTop - padY).coerceIn(0, max(0, source.height - 1))
        val right = (boxRight + padX).coerceIn(left + 1, source.width)
        val bottom = (boxBottom + padY).coerceIn(top + 1, source.height)

        val cropped = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        if (!mirrorHorizontal) return cropped
        val matrix = Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
    }
}
