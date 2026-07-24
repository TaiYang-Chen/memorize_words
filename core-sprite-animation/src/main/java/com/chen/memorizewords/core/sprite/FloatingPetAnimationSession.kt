package com.chen.memorizewords.core.sprite

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

typealias FloatingPetAnimationSession = SpritePlaybackSession

interface FloatingPetAnimationSessionFactory {
    suspend fun create(
        pack: SpritePack,
        host: FloatingPetRenderHost,
        scope: CoroutineScope
    ): FloatingPetAnimationSession
}

/** Creates the KTX2 GPU renderer used by all production character packages. */
class DefaultFloatingPetAnimationSessionFactory : FloatingPetAnimationSessionFactory {

    override suspend fun create(
        pack: SpritePack,
        host: FloatingPetRenderHost,
        scope: CoroutineScope
    ): FloatingPetAnimationSession = when (pack.manifest.schemaVersion) {
        SpritePackManifest.KTX2_SCHEMA_VERSION -> createGpu(pack, host)
        else -> throw IllegalArgumentException(
            "Only KTX2 schema-2 floating-pet packages are supported"
        )
    }

    private suspend fun createGpu(
        pack: SpritePack,
        host: FloatingPetRenderHost
    ): FloatingPetAnimationSession {
        SpritePackValidator().validate(pack.manifest)
        val gpuAssets = withContext(Dispatchers.IO) {
            val resolved = pack.manifest.textures.mapValues { (id, texture) ->
                val spec = texture as? Ktx2PagedTextureSpec
                    ?: throw IllegalArgumentException("Schema v2 contains a non-KTX2 texture")
                val source = pack.textureSource(id) as? SpriteAtlasSource.LocalFile
                    ?: throw IllegalArgumentException("KTX2 texture ${id.value} has no local file")
                val file = source.file.canonicalFile
                require(file.isFile) { "KTX2 texture ${spec.fileName} is missing" }
                val header = file.inputStream().buffered().use { input ->
                    Ktx2HeaderParser().parse(input, file.length())
                }
                Ktx2PagedTextureHeaderValidator().validate(spec, header)
                Ktx2TextureAsset(spec, file)
            }
            val residentTextureIds = resolveResidentTextureIds(pack.manifest)
            val residentBytes = residentTextureIds.sumOf { textureId ->
                requireNotNull(resolved[textureId]) {
                    "Resident KTX2 texture ${textureId.value} is missing"
                }.spec.estimatedGpuResidentBytes
            }
            require(residentBytes <= MAX_STANDARD_GPU_BYTES) {
                "Standard GPU sprite textures exceed the 16 MiB resident budget"
            }
            ResolvedGpuAssets(
                assets = resolved,
                residentTextureIds = residentTextureIds
            )
        }
        val view = withContext(Dispatchers.Main.immediate) {
            GpuSpriteTextureView(host.context).also { stageRenderer(host, it) }
        }
        return GpuSpritePlaybackSession(
            manifest = pack.manifest,
            assets = gpuAssets.assets,
            residentTextureIds = gpuAssets.residentTextureIds,
            host = host,
            view = view
        )
    }

    /**
     * Required standard actions and their continuations form the always-ready texture set.
     * Optional semantic/event textures stay cold until an explicit preload requests them.
     */
    private fun resolveResidentTextureIds(manifest: SpritePackManifest): Set<SpriteTextureId> {
        val residentTextureIds = LinkedHashSet<SpriteTextureId>()
        if (DEFAULT_SPRITE_TEXTURE_ID in manifest.textures) {
            residentTextureIds += DEFAULT_SPRITE_TEXTURE_ID
        }
        val pendingClips = ArrayDeque<SpriteClipId>()
        pendingClips.addLast(manifest.fallbackClipId)
        RESIDENT_SEMANTIC_KEYS.mapNotNull(manifest.semanticBindings::get).forEach {
            pendingClips.addLast(it)
        }
        val visitedClips = HashSet<SpriteClipId>()
        while (pendingClips.isNotEmpty()) {
            val clipId = pendingClips.removeFirst()
            if (!visitedClips.add(clipId)) continue
            val clip = manifest.clip(clipId)
            residentTextureIds += clip.textureId
            clip.nextClipId?.let { pendingClips.addLast(it) }
        }
        return residentTextureIds
    }

    private companion object {
        const val MAX_STANDARD_GPU_BYTES = 16L * 1_024L * 1_024L
        val RESIDENT_SEMANTIC_KEYS = setOf(
            "idle",
            "card_open",
            "card_visible",
            "card_close"
        )
    }
}

private data class ResolvedGpuAssets(
    val assets: Map<SpriteTextureId, Ktx2TextureAsset>,
    val residentTextureIds: Set<SpriteTextureId>
)

private fun stageRenderer(host: FloatingPetRenderHost, child: View) {
    checkMainThread()
    child.alpha = 0f
    host.addView(
        child,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    )
}

internal fun activateRenderer(host: FloatingPetRenderHost, child: View) {
    checkMainThread()
    child.alpha = 1f
    val staleChildren = ArrayList<View>()
    repeat(host.childCount) { index ->
        host.getChildAt(index).takeIf { it !== child }?.let(staleChildren::add)
    }
    staleChildren.forEach(host::removeView)
}

internal fun removeRenderer(host: FloatingPetRenderHost, child: View) {
    checkMainThread()
    if (child.parent === host) host.removeView(child)
}

internal fun removeRendererOnMain(
    host: FloatingPetRenderHost,
    child: View,
    removed: AtomicBoolean
) {
    if (!removed.compareAndSet(false, true)) return
    if (Looper.myLooper() == Looper.getMainLooper()) {
        removeRenderer(host, child)
    } else {
        Handler(Looper.getMainLooper()).post { removeRenderer(host, child) }
    }
}

private fun checkMainThread() {
    check(Looper.myLooper() == Looper.getMainLooper()) {
        "Floating-pet renderer changes must run on the main thread"
    }
}
