package com.chen.memorizewords.core.sprite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import java.io.Closeable
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface SpriteFrameProvider : Closeable {
    val frameWidth: Int
    val frameHeight: Int

    /** Ownership of [reusable] is transferred to the provider when it is non-null. */
    suspend fun decode(frameIndex: Int, reusable: Bitmap? = null): Bitmap
}

class BitmapRegionSpriteFrameProvider(
    private val atlas: SpriteAtlasSpec,
    source: SpriteAtlasSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : SpriteFrameProvider {
    private val decoderLock = Mutex()
    private val closeRequested = AtomicBoolean(false)
    private val cleanupScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val inputStream: InputStream = try {
        when (source) {
            is SpriteAtlasSource.LocalFile -> FileInputStream(source.file)
        }
    } catch (error: Throwable) {
        cleanupScope.cancel()
        throw error
    }
    private val decoder = try {
        val createdDecoder = requireNotNull(BitmapRegionDecoder.newInstance(inputStream, false)) {
            "Unable to create WebP region decoder"
        }
        try {
            require(createdDecoder.width == atlas.width && createdDecoder.height == atlas.height) {
                "Atlas dimensions do not match the manifest"
            }
            createdDecoder
        } catch (error: Throwable) {
            runCatching { createdDecoder.recycle() }
            throw error
        }
    } catch (error: Throwable) {
        runCatching { inputStream.close() }
        cleanupScope.cancel()
        throw error
    }
    private val decodeRect = Rect()
    private val decodeOptions = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inMutable = true
    }

    override val frameWidth: Int = atlas.frameWidth
    override val frameHeight: Int = atlas.frameHeight

    override suspend fun decode(frameIndex: Int, reusable: Bitmap?): Bitmap {
        var decodedResult: Bitmap? = null
        try {
            require(frameIndex in 0 until atlas.frameCount) { "Frame $frameIndex is out of bounds" }
            check(!closeRequested.get()) { "Sprite frame provider is closed" }
            return withContext(dispatcher) {
                decoderLock.withLock {
                    currentCoroutineContext().ensureActive()
                    check(!closeRequested.get()) { "Sprite frame provider is closed" }
                    val column = frameIndex % atlas.columns
                    val row = frameIndex / atlas.columns
                    decodeRect.set(
                        column * frameWidth,
                        row * frameHeight,
                        (column + 1) * frameWidth,
                        (row + 1) * frameHeight
                    )
                    decodeOptions.inBitmap = reusable?.takeIf {
                        it.isMutable &&
                            it.width == frameWidth &&
                            it.height == frameHeight &&
                            !it.isRecycled
                    }
                    try {
                        decodedResult = requireNotNull(
                            decoder.decodeRegion(decodeRect, decodeOptions)
                        ) {
                            "Unable to decode sprite frame $frameIndex"
                        }
                        if (reusable != null && decodedResult !== reusable) {
                            reusable.recycleSafely()
                        }
                        checkNotNull(decodedResult)
                    } catch (_: IllegalArgumentException) {
                        reusable?.recycleSafely()
                        decodeOptions.inBitmap = null
                        currentCoroutineContext().ensureActive()
                        check(!closeRequested.get()) { "Sprite frame provider is closed" }
                        decodedResult = requireNotNull(
                            decoder.decodeRegion(decodeRect, decodeOptions)
                        ) {
                            "Unable to decode sprite frame $frameIndex"
                        }
                        checkNotNull(decodedResult)
                    } finally {
                        decodeOptions.inBitmap = null
                    }
                }
            }
        } catch (error: Throwable) {
            decodedResult?.recycleSafely()
            reusable?.recycleSafely()
            throw error
        }
    }

    override fun close() {
        if (!closeRequested.compareAndSet(false, true)) return
        cleanupScope.launch {
            try {
                decoderLock.withLock {
                    closeResources()
                }
            } finally {
                cleanupScope.cancel()
            }
        }
    }

    private fun closeResources() {
        runCatching { decoder.recycle() }
        runCatching { inputStream.close() }
    }
}

private fun Bitmap.recycleSafely() {
    if (!isRecycled) recycle()
}
