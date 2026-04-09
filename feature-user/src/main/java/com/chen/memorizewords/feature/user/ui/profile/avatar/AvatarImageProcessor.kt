package com.chen.memorizewords.feature.user.ui.profile.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream

object AvatarImageProcessor {

    private const val MAX_EDGE = 1080
    private const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024
    private const val START_JPEG_QUALITY = 92
    private const val MIN_JPEG_QUALITY = 50
    private const val JPEG_QUALITY_STEP = 6
    private const val MIN_EDGE = 320
    private const val SCALE_DOWN_RATIO = 0.85f

    fun process(context: Context, uri: Uri): Result<ByteArray> = runCatching {
        val sourceBitmap = decodeBitmap(context, uri)
            ?: throw IllegalStateException("无法解析所选图片")
        var workingBitmap = scaleToMaxEdge(centerCropSquare(sourceBitmap), MAX_EDGE)
        var quality = START_JPEG_QUALITY
        var imageBytes = compressJpeg(workingBitmap, quality)

        while (imageBytes.size > MAX_UPLOAD_BYTES) {
            if (quality > MIN_JPEG_QUALITY) {
                quality = maxOf(MIN_JPEG_QUALITY, quality - JPEG_QUALITY_STEP)
            } else {
                val nextSize = (workingBitmap.width * SCALE_DOWN_RATIO).toInt()
                if (nextSize < MIN_EDGE) break
                workingBitmap = Bitmap.createScaledBitmap(workingBitmap, nextSize, nextSize, true)
            }
            imageBytes = compressJpeg(workingBitmap, quality)
        }

        if (imageBytes.size > MAX_UPLOAD_BYTES) {
            throw IllegalStateException("头像图片需小于5MB")
        }
        imageBytes
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = false
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }
    }

    private fun centerCropSquare(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }

    private fun scaleToMaxEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        if (bitmap.width <= maxEdge && bitmap.height <= maxEdge) return bitmap
        return Bitmap.createScaledBitmap(bitmap, maxEdge, maxEdge, true)
    }

    private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        return output.toByteArray()
    }
}
