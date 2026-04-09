package com.chen.memorizewords.data.repository.floating

import com.chen.memorizewords.data.remote.learningsync.toDomain
import com.chen.memorizewords.network.api.learningsync.FloatingSettingsDto
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingWordSettingsRepositoryImplTest {

    @Test
    fun `normalizeCardOpacityPercent clamps to valid range`() {
        assertEquals(0, normalizeCardOpacityPercent(-10))
        assertEquals(50, normalizeCardOpacityPercent(50))
        assertEquals(100, normalizeCardOpacityPercent(150))
    }

    @Test
    fun `floating settings dto preserves card opacity`() {
        val dto = FloatingSettingsDto(
            enabled = true,
            sourceType = "CURRENT_BOOK",
            orderType = "RANDOM",
            fieldConfigs = emptyList(),
            selectedWordIds = emptyList(),
            floatingBallX = 12,
            floatingBallY = 34,
            autoStartOnBoot = false,
            autoStartOnAppLaunch = true,
            cardOpacityPercent = 42
        )

        val settings = dto.toDomain()

        assertEquals(42, settings.cardOpacityPercent)
    }
}
