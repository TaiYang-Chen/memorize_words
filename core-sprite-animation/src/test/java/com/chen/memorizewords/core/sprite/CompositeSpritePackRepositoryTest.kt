package com.chen.memorizewords.core.sprite

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

class CompositeSpritePackRepositoryTest {
    @Test
    fun `uses source precedence and falls back to bundled pack`() = runTest {
        val fallbackId = SpritePackId("green_pet")
        val downloadedId = SpritePackId("downloaded_pet")
        val downloaded = pack(downloadedId)
        val bundled = pack(fallbackId)
        val repository = CompositeSpritePackRepository(
            sources = listOf(
                FakeSource(mapOf(downloadedId to downloaded)),
                FakeSource(mapOf(fallbackId to bundled))
            ),
            fallbackPackId = fallbackId
        )

        assertEquals(downloadedId, repository.get(downloadedId).manifest.packId)
        assertEquals(fallbackId, repository.get(SpritePackId("missing_pet")).manifest.packId)
    }

    @Test
    fun `continues to bundled source when an earlier source fails`() = runTest {
        val fallbackId = SpritePackId("green_pet")
        val bundled = pack(fallbackId)
        val repository = CompositeSpritePackRepository(
            sources = listOf(
                ThrowingSource(IllegalStateException("corrupt downloaded pack")),
                FakeSource(mapOf(fallbackId to bundled))
            ),
            fallbackPackId = fallbackId
        )

        assertEquals(fallbackId, repository.get(fallbackId).manifest.packId)
    }

    @Test
    fun `continues to a later source when a candidate fails consumer validation`() = runTest {
        val fallbackId = SpritePackId("green_pet")
        val invalidDownloaded = pack(fallbackId).copy(
            manifest = pack(fallbackId).manifest.copy(packVersion = 2)
        )
        val bundled = pack(fallbackId)
        val repository = CompositeSpritePackRepository(
            sources = listOf(
                FakeSource(mapOf(fallbackId to invalidDownloaded)),
                FakeSource(mapOf(fallbackId to bundled))
            ),
            fallbackPackId = fallbackId,
            validateCandidate = { manifest ->
                require(manifest.packVersion == 1) { "unsupported consumer contract" }
            }
        )

        assertEquals(1, repository.get(fallbackId).manifest.packVersion)
    }

    @Test
    fun `does not swallow cancellation from a source`() = runTest {
        val fallbackId = SpritePackId("green_pet")
        val repository = CompositeSpritePackRepository(
            sources = listOf(ThrowingSource(CancellationException("cancelled"))),
            fallbackPackId = fallbackId
        )

        assertFailsWith<CancellationException> {
            repository.get(fallbackId)
        }
    }

    @Test
    fun `returns null when no downloaded pack or fallback exists`() = runTest {
        val repository = CompositeSpritePackRepository(
            sources = listOf(FakeSource(emptyMap()))
        )

        assertNull(repository.find(SpritePackId("missing_pet")))
    }

    @Test
    fun exposesLaterSamePackSourceAsRuntimeFallback() = runTest {
        val packId = SpritePackId("green_pet")
        val base = pack(packId)
        val downloaded = base.copy(
            manifest = base.manifest.copy(packVersion = 2)
        )
        val repository = CompositeSpritePackRepository(
            sources = listOf(
                FakeSource(mapOf(packId to downloaded)),
                FakeSource(mapOf(packId to pack(packId)))
            )
        )

        val resolved = repository.get(packId)

        assertEquals(2, resolved.manifest.packVersion)
        assertEquals(1, resolved.runtimeFallback?.manifest?.packVersion)
        assertEquals(
            SpritePackRuntimeRole.LAST_KNOWN_GOOD,
            resolved.runtimeFallback?.runtimeRole
        )
    }

    @Test
    fun marksLaterSourceAsFallbackWhenEarlierCandidateFails() = runTest {
        val packId = SpritePackId("green_pet")
        val repository = CompositeSpritePackRepository(
            sources = listOf(
                ThrowingSource(IllegalStateException("corrupt installed revision")),
                FakeSource(mapOf(packId to pack(packId)))
            )
        )

        assertEquals(
            SpritePackRuntimeRole.LAST_KNOWN_GOOD,
            repository.get(packId).runtimeRole
        )
    }

    private fun pack(id: SpritePackId): SpritePack {
        val idle = SpriteClipId("idle")
        return SpritePack(
            manifest = SpritePackManifest(
                schemaVersion = 1,
                packId = id,
                packVersion = 1,
                atlas = SpriteAtlasSpec("sprite.webp", 1, 1, 1, 1, 1, 1),
                clips = mapOf(
                    idle to SpriteClipSpec(idle, 0, 1, 24, SpritePlaybackMode.HOLD_LAST)
                ),
                semanticBindings = mapOf("idle" to idle),
                fallbackClipId = idle
            ),
            atlasSource = SpriteAtlasSource.LocalFile(File("sprite.webp"))
        )
    }

    private class FakeSource(
        private val packs: Map<SpritePackId, SpritePack>
    ) : SpritePackSource {
        override suspend fun load(packId: SpritePackId): SpritePack? = packs[packId]
    }

    private class ThrowingSource(
        private val error: Exception
    ) : SpritePackSource {
        override suspend fun load(packId: SpritePackId): SpritePack? = throw error
    }
}
