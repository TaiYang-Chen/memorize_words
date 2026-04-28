package com.chen.memorizewords.data.repository.floating

import com.chen.memorizewords.data.remote.learningsync.toDomain
import com.chen.memorizewords.domain.model.floating.FloatingDockConfig
import com.chen.memorizewords.domain.model.floating.FloatingDockEdge
import com.chen.memorizewords.domain.model.floating.FloatingDockState
import com.chen.memorizewords.domain.model.floating.FloatingWordSettings
import com.chen.memorizewords.network.api.learningsync.FloatingDockConfigDto
import com.chen.memorizewords.network.api.learningsync.FloatingDockStateDto
import com.chen.memorizewords.network.api.learningsync.FloatingSettingsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloatingWordSettingsRepositoryImplTest {

    @Test
    fun `normalizeBallOpacityPercent clamps to valid range`() {
        assertEquals(0, normalizeBallOpacityPercent(-10))
        assertEquals(50, normalizeBallOpacityPercent(50))
        assertEquals(100, normalizeBallOpacityPercent(150))
    }

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
            ballOpacityPercent = 64,
            cardOpacityPercent = 42
        )

        val settings = dto.toDomain()

        assertEquals(64, settings.ballOpacityPercent)
        assertEquals(42, settings.cardOpacityPercent)
    }

    @Test
    fun `normalizeFloatingWordSettings clears dock state`() {
        val settings = normalizeFloatingWordSettings(
            FloatingWordSettings(
                dockConfig = FloatingDockConfig(),
                dockState = FloatingDockState(
                    dockedEdge = FloatingDockEdge.LEFT,
                    crossAxisPercent = 0.3f
                )
            )
        )

        assertNull(settings.dockState)
    }

    @Test
    fun `floating settings dto ignores dock state`() {
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
            ballOpacityPercent = 64,
            cardOpacityPercent = 42,
            dockConfig = FloatingDockConfigDto(
                snapTriggerDistanceDp = 24,
                halfHiddenEnabled = true,
                allowedEdges = listOf("LEFT", "RIGHT"),
                edgePriority = listOf("RIGHT", "LEFT"),
                snapAnimationDurationMs = 220L,
                tapExpandsCardAfterUnsnap = false,
                initialDockEdge = "RIGHT"
            ),
            dockState = FloatingDockStateDto(
                dockedEdge = "LEFT",
                crossAxisPercent = 0.75f
            )
        )

        val settings = dto.toDomain()

        assertNull(settings.dockState)
    }
}
