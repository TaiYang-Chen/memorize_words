package com.chen.memorizewords.core.sprite

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.AttributeSet
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred

internal data class Ktx2TextureAsset(
    val spec: Ktx2PagedTextureSpec,
    val file: File
)

/** Transparent TextureView whose EGL context and all texture work live on a dedicated thread. */
internal class GpuSpriteTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener {
    private val renderer = GpuSpriteGlRenderer()

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    suspend fun prepare(
        assets: Map<SpriteTextureId, Ktx2TextureAsset>,
        residentTextureIds: Set<SpriteTextureId>,
        initialClip: SpriteClipSpec,
        presentFrame: Boolean
    ) {
        renderer.prepare(assets, residentTextureIds, initialClip, presentFrame)
    }

    suspend fun preloadTexture(textureId: SpriteTextureId) =
        renderer.preloadTexture(textureId)

    fun activate() = renderer.activate()

    fun play(
        clip: SpriteClipSpec,
        generation: Long,
        onComplete: (Long) -> Unit,
        onError: (Long, Throwable) -> Unit
    ) = renderer.play(clip, generation, onComplete, onError)

    fun reverse(
        generation: Long,
        onComplete: (Long) -> Unit,
        onError: (Long, Throwable) -> Unit
    ) = renderer.reverse(generation, onComplete, onError)

    fun cancelPlayback() = renderer.cancelPlayback()

    fun trimMemory() = renderer.trimMemory()

    suspend fun trimMemoryAndAwait() = renderer.trimMemoryAndAwait()

    fun close() = renderer.close()

    suspend fun awaitReleased() = renderer.awaitReleased()

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        renderer.attachSurface(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        renderer.resizeSurface(surface, width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        renderer.detachSurface(surface, releaseTexture = true)
        // The GL thread releases it after EGL has stopped referencing the native window.
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
}

private class GpuSpriteGlRenderer {
    private val thread = HandlerThread("FloatingPet-GPU").apply { start() }
    private val handler = Handler(thread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val closeRequested = AtomicBoolean(false)
    private val released = CompletableDeferred<Unit>()
    private var choreographer: Choreographer? = null
    private var frameScheduled = false

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var nativeSurface: Surface? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var glMajorVersion = 0

    private var program = 0
    private var positionLocation = -1
    private var textureCoordinateLocation = -1
    private var colorSamplerLocation = -1
    private var alphaSamplerLocation = -1
    private var dualPlaneLocation = -1

    private val positions: FloatBuffer = directFloatBuffer(8)
    private val textureCoordinates: FloatBuffer = directFloatBuffer(8)
    private val resources = LinkedHashMap<SpriteTextureId, GpuTextureResource>()
    private val optionalTextureLru = LinkedHashMap<SpriteTextureId, Unit>()
    private var assets: Map<SpriteTextureId, Ktx2TextureAsset> = emptyMap()
    private var residentTextureIds: Set<SpriteTextureId> = emptySet()
    private var residentGpuBytes = 0L
    private var uploadedGpuBytes = 0L
    private var prepareRequest: PrepareRequest? = null
    private var prepared = false
    private var active = false
    private var residentFallbackFrame: GpuFrame? = null
    private var heldFrame: GpuFrame? = null
    private var playback: GpuPlayback? = null

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        frameScheduled = false
        if (closeRequested.get()) return@FrameCallback
        try {
            drawAt(frameTimeNanos)
        } catch (contextLost: EglContextLostException) {
            try {
                recoverEglContext()
            } catch (recoveryError: Throwable) {
                recoveryError.addSuppressed(contextLost)
                failActivePlayback(recoveryError)
            }
        } catch (error: Throwable) {
            failActivePlayback(error)
        }
    }

    suspend fun prepare(
        requestedAssets: Map<SpriteTextureId, Ktx2TextureAsset>,
        requestedResidentTextureIds: Set<SpriteTextureId>,
        initialClip: SpriteClipSpec,
        presentFrame: Boolean
    ) {
        require(requestedAssets.isNotEmpty()) { "A GPU sprite pack must contain textures" }
        require(requestedResidentTextureIds.isNotEmpty()) {
            "A GPU sprite pack must have at least one resident texture"
        }
        val copiedAssets = requestedAssets.toMap()
        val copiedResidentTextureIds = requestedResidentTextureIds.toSet()
        require(copiedResidentTextureIds.all(copiedAssets::containsKey)) {
            "A resident GPU texture is missing from the asset map"
        }
        require(initialClip.textureId in copiedResidentTextureIds) {
            "The initial GPU clip must use a resident texture"
        }
        val requestedResidentBytes = copiedResidentTextureIds.sumOf { textureId ->
            copiedAssets.getValue(textureId).spec.estimatedGpuResidentBytes
        }
        require(requestedResidentBytes <= MAX_STANDARD_GPU_TEXTURE_BYTES) {
            "Standard GPU textures exceed the 16 MiB resident budget"
        }
        val completion = CompletableDeferred<Unit>()
        handler.post {
            if (closeRequested.get()) {
                completion.completeExceptionally(IllegalStateException("GPU renderer is closed"))
                return@post
            }
            assets = copiedAssets
            residentTextureIds = copiedResidentTextureIds
            residentGpuBytes = requestedResidentBytes
            optionalTextureLru.clear()
            residentFallbackFrame = GpuFrame(initialClip.textureId, initialClip.startFrame)
            prepareRequest = PrepareRequest(initialClip, presentFrame, completion)
            prepareIfPossible()
        }
        completion.await()
    }

    suspend fun preloadTexture(textureId: SpriteTextureId) {
        val completion = CompletableDeferred<Unit>()
        handler.post {
            when {
                closeRequested.get() -> completion.completeExceptionally(
                    IllegalStateException("GPU renderer is closed")
                )
                !prepared -> completion.completeExceptionally(
                    IllegalStateException("GPU renderer is not prepared")
                )
                !hasCurrentEgl() -> completion.completeExceptionally(
                    IllegalStateException("GPU renderer surface is unavailable")
                )
                else -> try {
                    ensureTextureUploaded(textureId)
                    completion.complete(Unit)
                } catch (error: Throwable) {
                    completion.completeExceptionally(error)
                }
            }
        }
        completion.await()
    }

    fun activate() {
        handler.post {
            if (closeRequested.get() || !prepared) return@post
            active = true
            requestFrame()
        }
    }

    fun play(
        clip: SpriteClipSpec,
        generation: Long,
        onComplete: (Long) -> Unit,
        onError: (Long, Throwable) -> Unit
    ) {
        handler.post {
            if (closeRequested.get()) return@post
            if (!prepared) {
                reportPlaybackError(
                    generation,
                    onError,
                    IllegalStateException("GPU renderer is not prepared")
                )
                return@post
            }
            if (resources[clip.textureId] == null) {
                reportPlaybackError(
                    generation,
                    onError,
                    IllegalStateException(
                        "Texture ${clip.textureId.value} was not prefetched before play"
                    )
                )
                return@post
            }
            touchOptionalTexture(clip.textureId)
            val now = System.nanoTime()
            playback = GpuPlayback(
                clip = clip,
                reverse = false,
                startOffset = 0,
                startNanos = now,
                generation = generation,
                onComplete = onComplete,
                onError = onError
            )
            heldFrame = GpuFrame(clip.textureId, clip.startFrame)
            requestFrame()
        }
    }

    fun reverse(
        generation: Long,
        onComplete: (Long) -> Unit,
        onError: (Long, Throwable) -> Unit
    ) {
        handler.post {
            val current = playback ?: return@post
            if (closeRequested.get() || !prepared || !current.clip.reversible) return@post
            if (resources[current.clip.textureId] == null) {
                playback = null
                reportPlaybackError(
                    generation,
                    onError,
                    IllegalStateException(
                        "Texture ${current.clip.textureId.value} is no longer resident"
                    )
                )
                return@post
            }
            touchOptionalTexture(current.clip.textureId)
            val now = System.nanoTime()
            val currentOffset = current.resolveOffset(now).coerceIn(0, current.clip.frameCount - 1)
            playback = current.copy(
                reverse = !current.reverse,
                startOffset = currentOffset,
                startNanos = now,
                generation = generation,
                onComplete = onComplete,
                onError = onError,
                completionDelivered = false
            )
            heldFrame = GpuFrame(current.clip.textureId, current.clip.startFrame + currentOffset)
            requestFrame()
        }
    }

    fun cancelPlayback() {
        handler.post {
            playback?.let { current ->
                val offset = current.resolveOffset(System.nanoTime())
                    .coerceIn(0, current.clip.frameCount - 1)
                heldFrame = GpuFrame(current.clip.textureId, current.clip.startFrame + offset)
            }
            playback = null
            requestFrame()
        }
    }

    fun trimMemory() {
        handler.post { trimMemoryOnGlThread() }
    }

    suspend fun trimMemoryAndAwait() {
        if (closeRequested.get()) return
        val completion = CompletableDeferred<Unit>()
        val accepted = handler.post {
            try {
                trimMemoryOnGlThread()
                completion.complete(Unit)
            } catch (error: Throwable) {
                completion.completeExceptionally(error)
            }
        }
        if (!accepted) {
            completion.completeExceptionally(
                IllegalStateException("GPU renderer thread rejected the trim barrier")
            )
        }
        completion.await()
    }

    private fun trimMemoryOnGlThread() {
        if (closeRequested.get()) return
        val optionalPlayback = playback?.takeIf {
            it.clip.textureId !in residentTextureIds
        }
        if (optionalPlayback != null) {
            failActivePlayback(
                IllegalStateException("Optional GPU playback was evicted by trimMemory")
            )
        }
        if (heldFrame?.textureId?.let { it !in residentTextureIds } == true) {
            heldFrame = residentFallbackFrame
        }
        evictAllOptionalTextures()
        requestFrame()
    }

    fun attachSurface(texture: SurfaceTexture, width: Int, height: Int) {
        handler.post {
            if (closeRequested.get()) {
                texture.release()
                return@post
            }
            destroyEgl(releaseTexture = surfaceTexture !== texture)
            surfaceTexture = texture
            surfaceWidth = width
            surfaceHeight = height
            try {
                createEgl(texture)
                if (assets.isNotEmpty()) restoreTextureResources()
                prepareIfPossible()
                requestFrame()
            } catch (error: Throwable) {
                prepareRequest?.completion?.completeExceptionally(error)
                prepareRequest = null
                failActivePlayback(error)
                destroyEgl(releaseTexture = true)
            }
        }
    }

    fun resizeSurface(texture: SurfaceTexture, width: Int, height: Int) {
        handler.post {
            if (surfaceTexture !== texture) return@post
            surfaceWidth = width
            surfaceHeight = height
            if (hasCurrentEgl()) {
                GLES20.glViewport(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
                requestFrame()
            }
        }
    }

    fun detachSurface(texture: SurfaceTexture, releaseTexture: Boolean) {
        handler.post {
            if (surfaceTexture !== texture) {
                if (releaseTexture) texture.release()
                return@post
            }
            destroyEgl(releaseTexture)
        }
    }

    fun close() {
        if (!closeRequested.compareAndSet(false, true)) return
        handler.post {
            try {
                choreographer?.removeFrameCallback(frameCallback)
                frameScheduled = false
                prepareRequest?.completion?.completeExceptionally(
                    IllegalStateException("GPU renderer closed while preparing")
                )
                prepareRequest = null
                playback = null
                heldFrame = null
                destroyEgl(releaseTexture = true)
                assets = emptyMap()
                residentTextureIds = emptySet()
                optionalTextureLru.clear()
                residentGpuBytes = 0L
                residentFallbackFrame = null
                prepared = false
                active = false
            } finally {
                released.complete(Unit)
                thread.quitSafely()
            }
        }
    }

    suspend fun awaitReleased() = released.await()

    private fun prepareIfPossible() {
        val request = prepareRequest ?: return
        if (!hasCurrentEgl()) return
        try {
            ensureResidentTexturesUploaded()
            val initialFrame = GpuFrame(request.initialClip.textureId, request.initialClip.startFrame)
            residentFallbackFrame = initialFrame
            heldFrame = initialFrame
            playback = null
            prepared = true
            if (request.presentFrame) drawFrame(initialFrame)
            validateGl("preparing first sprite frame")
            if (!request.presentFrame) {
                // A hidden staged renderer still validates a real first draw before activation.
                drawFrame(initialFrame)
            }
            prepareRequest = null
            request.completion.complete(Unit)
        } catch (error: Throwable) {
            prepared = false
            prepareRequest = null
            request.completion.completeExceptionally(error)
        }
    }

    private fun drawAt(frameTimeNanos: Long) {
        if (!active || !prepared || !hasCurrentEgl()) return
        val current = playback
        if (current == null) {
            heldFrame?.let(::drawFrame)
            return
        }
        val resolved = current.resolve(frameTimeNanos)
        val frame = GpuFrame(current.clip.textureId, current.clip.startFrame + resolved.offset)
        heldFrame = frame
        drawFrame(frame)
        if (resolved.completed && !current.completionDelivered) {
            current.completionDelivered = true
            playback = null
            mainHandler.post { current.onComplete(current.generation) }
        } else if (current.clip.playbackMode == SpritePlaybackMode.LOOP || !resolved.completed) {
            requestFrame()
        }
    }

    private fun drawFrame(frame: GpuFrame) {
        if (!hasCurrentEgl()) return
        val resource = resources[frame.textureId]
            ?: throw IllegalStateException("Texture ${frame.textureId.value} is not uploaded")
        touchOptionalTexture(frame.textureId)
        val spec = resource.spec
        require(frame.frameIndex in 0 until spec.frameCount) { "GPU frame is out of range" }
        val framesPerPage = spec.framesPerPage
        val page = frame.frameIndex / framesPerPage
        val localFrame = frame.frameIndex % framesPerPage
        val column = localFrame % spec.columns
        val row = localFrame / spec.columns

        GLES20.glViewport(0, 0, surfaceWidth.coerceAtLeast(1), surfaceHeight.coerceAtLeast(1))
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        updatePositionBuffer(spec.frameWidth, spec.frameHeight)
        updateTextureCoordinateBuffer(spec, column, row)
        positions.position(0)
        textureCoordinates.position(0)
        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 0, positions)
        GLES20.glEnableVertexAttribArray(textureCoordinateLocation)
        GLES20.glVertexAttribPointer(
            textureCoordinateLocation,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            textureCoordinates
        )

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, resource.colorTextures[page])
        GLES20.glUniform1i(colorSamplerLocation, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        val alphaTexture = resource.alphaTextures?.get(page) ?: resource.colorTextures[page]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, alphaTexture)
        GLES20.glUniform1i(alphaSamplerLocation, 1)
        GLES20.glUniform1i(dualPlaneLocation, if (resource.alphaTextures != null) 1 else 0)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionLocation)
        GLES20.glDisableVertexAttribArray(textureCoordinateLocation)
        validateGl("drawing sprite frame")
        if (!EGL14.eglSwapBuffers(display, eglSurface)) {
            val error = EGL14.eglGetError()
            if (error == EGL14.EGL_CONTEXT_LOST) {
                throw EglContextLostException("GPU sprite EGL context was lost")
            }
            throw IllegalStateException("Unable to present GPU sprite frame: EGL $error")
        }
    }

    private fun ensureResidentTexturesUploaded() {
        check(hasCurrentEgl()) { "EGL must be ready before texture upload" }
        residentTextureIds.forEach { textureId ->
            if (resources.containsKey(textureId)) return@forEach
            val asset = requireNotNull(assets[textureId]) {
                "Missing resident KTX2 texture ${textureId.value}"
            }
            registerTexture(textureId, uploadTexture(asset))
        }
        validateGl("uploading resident sprite textures")
    }

    private fun restoreTextureResources() {
        check(hasCurrentEgl()) { "EGL must be ready before texture restore" }
        ensureResidentTexturesUploaded()
        val optionalToRestore = optionalTextureLru.keys.toList()
        optionalTextureLru.clear()
        optionalToRestore.forEach { textureId ->
            try {
                ensureTextureUploaded(textureId)
            } catch (error: Throwable) {
                if (heldFrame?.textureId == textureId) {
                    heldFrame = residentFallbackFrame
                }
                if (playback?.clip?.textureId == textureId) {
                    failActivePlayback(error)
                }
            }
        }
    }

    private fun recoverEglContext() {
        val texture = surfaceTexture
            ?: throw IllegalStateException("Cannot recover GPU context without a SurfaceTexture")
        destroyEgl(releaseTexture = false)
        surfaceTexture = texture
        try {
            createEgl(texture)
            restoreTextureResources()
            prepareIfPossible()
            requestFrame()
        } catch (error: Throwable) {
            surfaceTexture = texture
            destroyEgl(releaseTexture = false)
            surfaceTexture = texture
            throw error
        }
    }

    private fun ensureTextureUploaded(textureId: SpriteTextureId) {
        if (resources.containsKey(textureId)) {
            touchOptionalTexture(textureId)
            return
        }
        val asset = requireNotNull(assets[textureId]) {
            "Unknown GPU texture ${textureId.value}"
        }
        val requiredBytes = asset.spec.estimatedGpuResidentBytes
        if (textureId !in residentTextureIds) {
            makeRoomForOptionalTexture(requiredBytes)
        }
        val resource = uploadTexture(asset)
        try {
            registerTexture(textureId, resource)
            validateGl("uploading sprite texture ${textureId.value}")
        } catch (error: Throwable) {
            if (resources[textureId] === resource) {
                resources.remove(textureId)
                uploadedGpuBytes = (uploadedGpuBytes - resource.gpuByteCount).coerceAtLeast(0L)
            }
            optionalTextureLru.remove(textureId)
            deleteTextureResource(resource)
            throw error
        }
    }

    private fun makeRoomForOptionalTexture(requiredBytes: Long) {
        val optionalBudget = MAX_RUNTIME_GPU_TEXTURE_BYTES - residentGpuBytes
        require(requiredBytes <= optionalBudget) {
            "Optional GPU texture exceeds the remaining runtime budget"
        }
        if (uploadedGpuBytes <= MAX_RUNTIME_GPU_TEXTURE_BYTES - requiredBytes) return
        val protectedTextureId = playback?.clip?.textureId
        optionalTextureLru.keys.toList().forEach { textureId ->
            if (textureId != protectedTextureId) {
                evictOptionalTexture(textureId)
                if (uploadedGpuBytes <= MAX_RUNTIME_GPU_TEXTURE_BYTES - requiredBytes) {
                    return
                }
            }
        }
        throw IllegalStateException(
            "No evictable optional GPU texture can satisfy the 32 MiB runtime budget"
        )
    }

    private fun registerTexture(
        textureId: SpriteTextureId,
        resource: GpuTextureResource
    ) {
        check(textureId !in resources) { "GPU texture ${textureId.value} is already uploaded" }
        check(uploadedGpuBytes <= MAX_RUNTIME_GPU_TEXTURE_BYTES - resource.gpuByteCount) {
            "GPU texture upload would exceed the 32 MiB runtime budget"
        }
        resources[textureId] = resource
        uploadedGpuBytes += resource.gpuByteCount
        touchOptionalTexture(textureId)
    }

    private fun touchOptionalTexture(textureId: SpriteTextureId) {
        if (textureId in residentTextureIds || textureId !in resources) return
        optionalTextureLru.remove(textureId)
        optionalTextureLru[textureId] = Unit
    }

    private fun evictAllOptionalTextures() {
        val optionalIds = LinkedHashSet<SpriteTextureId>()
        optionalIds.addAll(optionalTextureLru.keys)
        resources.keys.filterTo(optionalIds) { it !in residentTextureIds }
        optionalIds.forEach(::evictOptionalTexture)
        optionalTextureLru.clear()
    }

    private fun evictOptionalTexture(textureId: SpriteTextureId) {
        if (textureId in residentTextureIds) return
        optionalTextureLru.remove(textureId)
        val resource = resources.remove(textureId) ?: return
        deleteTextureResource(resource)
        uploadedGpuBytes = (uploadedGpuBytes - resource.gpuByteCount).coerceAtLeast(0L)
        if (heldFrame?.textureId == textureId) {
            heldFrame = residentFallbackFrame
        }
    }

    private fun deleteTextureResource(resource: GpuTextureResource) {
        if (!hasCurrentEgl()) return
        GLES20.glDeleteTextures(resource.colorTextures.size, resource.colorTextures, 0)
        resource.alphaTextures?.let { GLES20.glDeleteTextures(it.size, it, 0) }
    }

    private fun uploadTexture(asset: Ktx2TextureAsset): GpuTextureResource {
        val spec = asset.spec
        require(asset.file.isFile) { "Missing KTX2 texture ${asset.file.name}" }
        BasisKtx2Native.ensureLoaded()
        val handle = BasisKtx2Native.nativeCreate(asset.file.absolutePath)
        require(handle != 0L) { "Unable to open Basis KTX2 texture ${asset.file.name}" }
        try {
            val info = BasisKtx2Native.nativeGetInfo(handle)
            require(info.size >= 8) { "Invalid native KTX2 texture metadata" }
            require(
                info[0] == spec.pageWidth &&
                    info[1] == spec.pageHeight &&
                    info[2] == spec.pageCount
            ) { "Native KTX2 geometry does not match the manifest" }
            val hasAlpha = info[3] != 0
            require(hasAlpha || spec.alphaMode == SpriteAlphaMode.OPAQUE) {
                "KTX2 texture is missing its alpha channel"
            }
            require(info[4] != 0 && spec.colorSpace == SpriteColorSpace.SRGB) {
                "KTX2 texture transfer function is not sRGB"
            }
            require(info[5] == 166 && info[6] == 1 && info[7] == 3) {
                "KTX2 texture DFD is not UASTC RGBA"
            }
            if (info.size > 9) {
                require(info[8] == 0 && info[9] == 1) {
                    "KTX2 texture must use straight alpha and BT.709 primaries"
                }
            }

            val colorTextures = IntArray(spec.pageCount)
            GLES20.glGenTextures(colorTextures.size, colorTextures, 0)
            val alphaTextures = if (glMajorVersion >= 3 || !hasAlpha) {
                null
            } else {
                IntArray(spec.pageCount).also { GLES20.glGenTextures(it.size, it, 0) }
            }
            try {
                repeat(spec.pageCount) { page ->
                    if (glMajorVersion >= 3) {
                        val rgba = BasisKtx2Native.nativeTranscodePage(
                            handle,
                            page,
                            BasisKtx2Native.TARGET_ETC2_RGBA,
                            false
                        )
                        uploadCompressedPage(
                            textureId = colorTextures[page],
                            internalFormat = GLES30.GL_COMPRESSED_RGBA8_ETC2_EAC,
                            width = spec.pageWidth,
                            height = spec.pageHeight,
                            bytes = rgba,
                            expectedBytes = compressedByteCount(spec.pageWidth, spec.pageHeight, 16)
                        )
                    } else {
                        val rgb = BasisKtx2Native.nativeTranscodePage(
                            handle,
                            page,
                            BasisKtx2Native.TARGET_ETC1_RGB,
                            false
                        )
                        uploadCompressedPage(
                            textureId = colorTextures[page],
                            internalFormat = ETC1_RGB8_OES,
                            width = spec.pageWidth,
                            height = spec.pageHeight,
                            bytes = rgb,
                            expectedBytes = compressedByteCount(spec.pageWidth, spec.pageHeight, 8)
                        )
                        if (alphaTextures != null) {
                            val alpha = BasisKtx2Native.nativeTranscodePage(
                                handle,
                                page,
                                BasisKtx2Native.TARGET_ETC1_RGB,
                                true
                            )
                            uploadCompressedPage(
                                textureId = alphaTextures[page],
                                internalFormat = ETC1_RGB8_OES,
                                width = spec.pageWidth,
                                height = spec.pageHeight,
                                bytes = alpha,
                                expectedBytes = compressedByteCount(spec.pageWidth, spec.pageHeight, 8)
                            )
                        }
                    }
                }
                return GpuTextureResource(
                    spec = spec,
                    colorTextures = colorTextures,
                    alphaTextures = alphaTextures,
                    gpuByteCount = spec.estimatedGpuResidentBytes
                )
            } catch (error: Throwable) {
                GLES20.glDeleteTextures(colorTextures.size, colorTextures, 0)
                alphaTextures?.let { GLES20.glDeleteTextures(it.size, it, 0) }
                throw error
            }
        } finally {
            BasisKtx2Native.nativeDestroy(handle)
        }
    }

    private fun uploadCompressedPage(
        textureId: Int,
        internalFormat: Int,
        width: Int,
        height: Int,
        bytes: ByteArray,
        expectedBytes: Int
    ) {
        require(bytes.size == expectedBytes) {
            "Unexpected transcoded texture size ${bytes.size}, expected $expectedBytes"
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buffer.put(bytes).position(0)
        GLES20.glCompressedTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            internalFormat,
            width,
            height,
            0,
            bytes.size,
            buffer
        )
    }

    private fun createEgl(texture: SurfaceTexture) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "Unable to obtain EGL display" }
        val versions = IntArray(2)
        check(EGL14.eglInitialize(display, versions, 0, versions, 1)) {
            "Unable to initialize EGL"
        }
        EGL14.eglBindAPI(EGL14.EGL_OPENGL_ES_API)
        val contextResult = createContext(3) ?: createContext(2)
            ?: throw IllegalStateException("Unable to create GLES2/3 context")
        context = contextResult.context
        glMajorVersion = contextResult.majorVersion
        nativeSurface = Surface(texture)
        eglSurface = EGL14.eglCreateWindowSurface(
            display,
            contextResult.config,
            nativeSurface,
            intArrayOf(EGL14.EGL_NONE),
            0
        )
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "Unable to create transparent EGL surface" }
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
            "Unable to make sprite EGL context current"
        }
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionLocation = GLES20.glGetAttribLocation(program, "aPosition")
        textureCoordinateLocation = GLES20.glGetAttribLocation(program, "aTexCoord")
        colorSamplerLocation = GLES20.glGetUniformLocation(program, "uColor")
        alphaSamplerLocation = GLES20.glGetUniformLocation(program, "uAlpha")
        dualPlaneLocation = GLES20.glGetUniformLocation(program, "uDualPlane")
        check(
            positionLocation >= 0 && textureCoordinateLocation >= 0 &&
                colorSamplerLocation >= 0 && alphaSamplerLocation >= 0 && dualPlaneLocation >= 0
        ) { "GPU sprite shader interface is incomplete" }
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glViewport(0, 0, surfaceWidth.coerceAtLeast(1), surfaceHeight.coerceAtLeast(1))
        choreographer = Choreographer.getInstance()
        validateGl("initializing sprite renderer")
    }

    private fun createContext(majorVersion: Int): EglContextResult? {
        val renderableType = if (majorVersion >= 3) EGL_OPENGL_ES3_BIT_KHR else EGL14.EGL_OPENGL_ES2_BIT
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_RENDERABLE_TYPE, renderableType,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0)) return null
        val config = configs.firstOrNull() ?: return null
        val created = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, majorVersion, EGL14.EGL_NONE),
            0
        )
        if (created == EGL14.EGL_NO_CONTEXT) return null
        return EglContextResult(config, created, majorVersion)
    }

    private fun destroyEgl(releaseTexture: Boolean) {
        choreographer?.removeFrameCallback(frameCallback)
        choreographer = null
        frameScheduled = false
        if (display != EGL14.EGL_NO_DISPLAY) {
            if (context != EGL14.EGL_NO_CONTEXT && eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)
                deleteTextureResources()
                if (program != 0) GLES20.glDeleteProgram(program)
            }
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }
        resources.clear()
        uploadedGpuBytes = 0L
        program = 0
        positionLocation = -1
        textureCoordinateLocation = -1
        colorSamplerLocation = -1
        alphaSamplerLocation = -1
        dualPlaneLocation = -1
        nativeSurface?.release()
        nativeSurface = null
        val texture = surfaceTexture
        surfaceTexture = null
        if (releaseTexture) texture?.release()
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
        glMajorVersion = 0
    }

    private fun deleteTextureResources() {
        resources.values.forEach(::deleteTextureResource)
        resources.clear()
        uploadedGpuBytes = 0L
    }

    private fun hasCurrentEgl(): Boolean =
        display != EGL14.EGL_NO_DISPLAY &&
            context != EGL14.EGL_NO_CONTEXT &&
            eglSurface != EGL14.EGL_NO_SURFACE

    private fun requestFrame() {
        if (frameScheduled || !hasCurrentEgl() || closeRequested.get()) return
        frameScheduled = true
        checkNotNull(choreographer).postFrameCallback(frameCallback)
    }

    private fun reportPlaybackError(
        generation: Long,
        onError: (Long, Throwable) -> Unit,
        error: Throwable
    ) {
        mainHandler.post { onError(generation, error) }
    }

    private fun failActivePlayback(error: Throwable) {
        val failed = playback
        playback = null
        if (failed != null) mainHandler.post { failed.onError(failed.generation, error) }
    }

    private fun updatePositionBuffer(frameWidth: Int, frameHeight: Int) {
        val viewWidth = surfaceWidth.coerceAtLeast(1).toFloat()
        val viewHeight = surfaceHeight.coerceAtLeast(1).toFloat()
        val frameAspect = frameWidth.toFloat() / frameHeight.coerceAtLeast(1)
        val viewAspect = viewWidth / viewHeight
        val scaleX: Float
        val scaleY: Float
        if (frameAspect > viewAspect) {
            scaleX = 1f
            scaleY = viewAspect / frameAspect
        } else {
            scaleX = frameAspect / viewAspect
            scaleY = 1f
        }
        positions.position(0)
        positions.put(
            floatArrayOf(
                -scaleX, -scaleY,
                scaleX, -scaleY,
                -scaleX, scaleY,
                scaleX, scaleY
            )
        ).position(0)
    }

    private fun updateTextureCoordinateBuffer(
        spec: Ktx2PagedTextureSpec,
        column: Int,
        row: Int
    ) {
        // Half-texel inset prevents linear sampling from bleeding into adjacent sprite cells.
        val u0 = ((column * spec.frameWidth) + 0.5f) / spec.pageWidth
        val u1 = (((column + 1) * spec.frameWidth) - 0.5f) / spec.pageWidth
        val vTop = ((row * spec.frameHeight) + 0.5f) / spec.pageHeight
        val vBottom = (((row + 1) * spec.frameHeight) - 0.5f) / spec.pageHeight
        textureCoordinates.position(0)
        textureCoordinates.put(
            floatArrayOf(
                u0, vBottom,
                u1, vBottom,
                u0, vTop,
                u1, vTop
            )
        ).position(0)
    }

    private fun validateGl(operation: String) {
        val error = GLES20.glGetError()
        if (error == GL_CONTEXT_LOST_KHR) {
            throw EglContextLostException("GPU sprite GL context was lost while $operation")
        }
        check(error == GLES20.GL_NO_ERROR) { "GL error $error while $operation" }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        return try {
            val result = GLES20.glCreateProgram()
            check(result != 0) { "Unable to create GPU sprite shader program" }
            GLES20.glAttachShader(result, vertex)
            GLES20.glAttachShader(result, fragment)
            GLES20.glLinkProgram(result)
            val linked = IntArray(1)
            GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, linked, 0)
            if (linked[0] == 0) {
                val message = GLES20.glGetProgramInfoLog(result)
                GLES20.glDeleteProgram(result)
                throw IllegalStateException("Unable to link GPU sprite shader: $message")
            }
            result
        } finally {
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        check(shader != 0) { "Unable to allocate GPU sprite shader" }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val message = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Unable to compile GPU sprite shader: $message")
        }
        return shader
    }

    private data class PrepareRequest(
        val initialClip: SpriteClipSpec,
        val presentFrame: Boolean,
        val completion: CompletableDeferred<Unit>
    )

    private data class GpuFrame(
        val textureId: SpriteTextureId,
        val frameIndex: Int
    )

    private data class GpuTextureResource(
        val spec: Ktx2PagedTextureSpec,
        val colorTextures: IntArray,
        val alphaTextures: IntArray?,
        val gpuByteCount: Long
    )

    private data class EglContextResult(
        val config: EGLConfig,
        val context: EGLContext,
        val majorVersion: Int
    )

    private data class ResolvedPlayback(
        val offset: Int,
        val completed: Boolean
    )

    private data class GpuPlayback(
        val clip: SpriteClipSpec,
        val reverse: Boolean,
        val startOffset: Int,
        val startNanos: Long,
        val generation: Long,
        val onComplete: (Long) -> Unit,
        val onError: (Long, Throwable) -> Unit,
        var completionDelivered: Boolean = false
    ) {
        fun resolveOffset(nowNanos: Long): Int = resolve(nowNanos).offset

        fun resolve(nowNanos: Long): ResolvedPlayback {
            val elapsedNanos = (nowNanos - startNanos).coerceAtLeast(0L)
            val elapsedFrames = ((elapsedNanos * clip.framesPerSecond) / NANOS_PER_SECOND)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            if (clip.playbackMode == SpritePlaybackMode.LOOP) {
                val direction = if (reverse) -1 else 1
                val raw = startOffset.toLong() + direction.toLong() * elapsedFrames
                return ResolvedPlayback(Math.floorMod(raw, clip.frameCount.toLong()).toInt(), false)
            }
            val lastOffset = clip.frameCount - 1
            return if (reverse) {
                val raw = startOffset - elapsedFrames
                ResolvedPlayback(raw.coerceAtLeast(0), raw < 0)
            } else {
                val raw = startOffset + elapsedFrames
                ResolvedPlayback(raw.coerceAtMost(lastOffset), raw > lastOffset)
            }
        }
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val ETC1_RGB8_OES = 0x8D64
        const val EGL_OPENGL_ES3_BIT_KHR = 0x40
        const val GL_CONTEXT_LOST_KHR = 0x0507
        const val MAX_STANDARD_GPU_TEXTURE_BYTES = 16L * 1_024L * 1_024L
        const val MAX_RUNTIME_GPU_TEXTURE_BYTES = 32L * 1_024L * 1_024L

        const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vTexCoord = aTexCoord;
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uColor;
            uniform sampler2D uAlpha;
            uniform int uDualPlane;
            varying vec2 vTexCoord;
            void main() {
                vec4 color = texture2D(uColor, vTexCoord);
                float alpha = uDualPlane == 1 ? texture2D(uAlpha, vTexCoord).r : color.a;
                gl_FragColor = vec4(color.rgb * alpha, alpha);
            }
        """
    }
}

private class EglContextLostException(message: String) : IllegalStateException(message)

private fun directFloatBuffer(floatCount: Int): FloatBuffer =
    ByteBuffer.allocateDirect(floatCount * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

private fun compressedByteCount(width: Int, height: Int, bytesPerBlock: Int): Int {
    val blocksWide = (width.toLong() + 3L) / 4L
    val blocksHigh = (height.toLong() + 3L) / 4L
    return Math.multiplyExact(Math.multiplyExact(blocksWide, blocksHigh), bytesPerBlock.toLong())
        .let(Math::toIntExact)
}
