package com.chen.memorizewords.data.floating.local

import com.chen.memorizewords.domain.floating.model.CharacterPackCatalogItem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharacterPackCatalogValidationTest {
    @Test
    fun `valid catalog item is accepted`() {
        assertTrue(CharacterPackLocalStore.isValidCatalogItem(validItem()))
    }

    @Test
    fun `unsupported or unverifiable catalog items are rejected`() {
        assertFalse(
            CharacterPackLocalStore.isValidCatalogItem(
                validItem().copy(manifestSchemaVersion = 2)
            )
        )
        assertFalse(
            CharacterPackLocalStore.isValidCatalogItem(
                validItem().copy(packageSha256 = "invalid")
            )
        )
        assertFalse(
            CharacterPackLocalStore.isValidCatalogItem(
                validItem().copy(packageSizeBytes = 0L)
            )
        )
        assertFalse(
            CharacterPackLocalStore.isValidCatalogItem(
                validItem().copy(packageUrl = "not-a-url")
            )
        )
        assertFalse(
            CharacterPackLocalStore.isValidCatalogItem(
                validItem().copy(packageUrl = "http://example.com/green_pet.zip")
            )
        )
        assertFalse(
            CharacterPackLocalStore.isValidCatalogItem(
                validItem().copy(packageUrl = "https://user:secret@example.com/green_pet.zip")
            )
        )
        assertFalse(
            CharacterPackLocalStore.isValidCatalogItem(
                validItem().copy(previewUrl = "https://example.com/green_pet.png#fragment")
            )
        )
        assertFalse(
            CharacterPackLocalStore.isValidCatalogItem(
                validItem().copy(description = "x".repeat(501))
            )
        )
        assertFalse(
            CharacterPackLocalStore.isValidCatalogItem(
                validItem().copy(packageUrl = "https://example.com/${"x".repeat(2_100)}")
            )
        )
    }

    @Test
    fun `complete catalog requires exactly one default and unique pack ids`() {
        val default = validItem().copy(isDefault = true)
        val other = validItem().copy(packId = "blue_pet")

        assertTrue(CharacterPackLocalStore.isValidCompleteCatalog(listOf(default, other)))
        assertFalse(CharacterPackLocalStore.isValidCompleteCatalog(listOf(default.copy(isDefault = false), other)))
        assertFalse(CharacterPackLocalStore.isValidCompleteCatalog(listOf(default, other.copy(isDefault = true))))
        assertFalse(CharacterPackLocalStore.isValidCompleteCatalog(listOf(default, default.copy(packVersion = 2))))
    }

    private fun validItem() = CharacterPackCatalogItem(
        packId = "green_pet",
        packVersion = 1,
        displayName = "绿宠",
        previewUrl = "https://example.com/green_pet.png",
        packageUrl = "https://example.com/green_pet.zip",
        packageSha256 = "a".repeat(64),
        packageSizeBytes = 1_421_233L,
        manifestSchemaVersion = 1,
        updatedAtMs = 1L
    )
}
