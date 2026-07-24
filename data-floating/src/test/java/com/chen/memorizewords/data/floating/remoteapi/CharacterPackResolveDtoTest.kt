package com.chen.memorizewords.data.floating.remoteapi

import com.chen.memorizewords.domain.floating.model.CharacterPackResolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CharacterPackResolveDtoTest {
    @Test
    fun `resolved response maps the server pack including default metadata`() {
        val result = CharacterPackResolveDto(
            status = "RESOLVED",
            pack = catalogItem(isDefault = true)
        ).toDomain()

        assertTrue(result is CharacterPackResolution.Resolved)
        assertEquals("blue_pet", result.item.packId)
        assertTrue(result.item.isDefault)
    }

    @Test
    fun `selection required is a successful resolution state`() {
        assertEquals(
            CharacterPackResolution.SelectionRequired,
            CharacterPackResolveDto(status = "SELECTION_REQUIRED").toDomain()
        )
    }

    @Test
    fun `malformed resolve responses are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CharacterPackResolveDto(status = "RESOLVED").toDomain()
        }
        assertFailsWith<IllegalArgumentException> {
            CharacterPackResolveDto(
                status = "SELECTION_REQUIRED",
                pack = catalogItem()
            ).toDomain()
        }
        assertFailsWith<IllegalArgumentException> {
            CharacterPackResolveDto(status = "UNKNOWN", pack = catalogItem()).toDomain()
        }
    }

    @Test
    fun `apply request contains only the selected pack id`() {
        assertEquals("blue_pet", ApplyCharacterPackRequest("blue_pet").packId)
    }

    private fun catalogItem(isDefault: Boolean = false) = CharacterPackDto(
        packId = "blue_pet",
        packVersion = 1,
        displayName = "Blue Pet",
        isDefault = isDefault,
        previewUrl = "https://cdn.example.com/blue_pet.png",
        packageUrl = "https://cdn.example.com/blue_pet.zip",
        packageSha256 = "a".repeat(64),
        packageSizeBytes = 1_000L,
        manifestSchemaVersion = 2,
        updatedAtMs = 1L
    )
}
