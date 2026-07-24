package com.chen.memorizewords.data.floating.repository

import com.chen.memorizewords.domain.floating.model.InstalledCharacterPack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CharacterPackRuntimeInstallPolicyTest {
    @Test
    fun readyPreviousRevisionBecomesLastKnownGood() {
        val previous = installed(version = 1, directory = "/packs/1-ready")

        val fallback = CharacterPackRuntimeInstallPolicy.fallbackFor(
            required = true,
            previous = previous
        )

        assertEquals(1, fallback?.packVersion)
        assertEquals(previous.installedDirectory, fallback?.installedDirectory)
    }

    @Test
    fun pendingPreviousRevisionCarriesForwardItsLastKnownGood() {
        val previous = installed(version = 2, directory = "/packs/2-pending").copy(
            pendingRuntimeValidation = true,
            lastKnownGoodVersion = 1,
            lastKnownGoodDirectory = "/packs/1-ready"
        )

        val fallback = CharacterPackRuntimeInstallPolicy.fallbackFor(
            required = true,
            previous = previous
        )

        assertEquals(1, fallback?.packVersion)
        assertEquals("/packs/1-ready", fallback?.installedDirectory)
    }

    @Test
    fun legacyInstallDoesNotRetainRuntimeFallback() {
        assertNull(
            CharacterPackRuntimeInstallPolicy.fallbackFor(
                required = false,
                previous = installed(version = 1, directory = "/packs/1-ready")
            )
        )
    }

    private fun installed(version: Int, directory: String) = InstalledCharacterPack(
        packId = "green_pet",
        packVersion = version,
        displayName = "Green pet",
        installedDirectory = directory,
        installedAtMs = 1L
    )
}
