package com.chen.memorizewords.data.floating.repository

import com.chen.memorizewords.domain.floating.model.FloatingDockConfig
import com.chen.memorizewords.domain.floating.model.FloatingDockEdge
import com.chen.memorizewords.domain.floating.model.FloatingDockState
import com.chen.memorizewords.domain.floating.model.FloatingWordFieldConfig
import com.chen.memorizewords.domain.floating.model.FloatingWordFieldType
import com.chen.memorizewords.domain.floating.model.FloatingWordOrderType
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceType
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

private data class FloatingDockConfigDto(
    val snapTriggerDistanceDp: Int = 16,
    val halfHiddenEnabled: Boolean = true,
    val allowedEdges: List<String> = listOf("LEFT", "RIGHT"),
    val edgePriority: List<String> = listOf("RIGHT", "LEFT"),
    val snapAnimationDurationMs: Long = 180L,
    val tapExpandsCardAfterUnsnap: Boolean = true,
    val initialDockEdge: String = "RIGHT"
)

private data class FloatingDockStateDto(
    val dockedEdge: String? = null,
    val crossAxisPercent: Float = 0.5f
)

private data class FloatingFieldConfigDto(
    val type: String,
    val enabled: Boolean,
    val fontSizeSp: Int
)

private data class FloatingSettingsDto(
    val enabled: Boolean,
    val sourceType: String,
    val orderType: String,
    val fieldConfigs: List<FloatingFieldConfigDto>,
    val selectedWordIds: List<Long>,
    val floatingBallX: Int,
    val floatingBallY: Int,
    val autoStartOnBoot: Boolean,
    val autoStartOnAppLaunch: Boolean,
    val ballOpacityPercent: Int,
    val cardOpacityPercent: Int,
    val dockConfig: FloatingDockConfigDto? = null,
    val dockState: FloatingDockStateDto? = null
)

private fun FloatingSettingsDto.toDomain(): FloatingWordSettings {
    return FloatingWordSettings(
        enabled = enabled,
        sourceType = runCatching { FloatingWordSourceType.valueOf(sourceType) }
            .getOrDefault(FloatingWordSourceType.CURRENT_BOOK),
        orderType = runCatching { FloatingWordOrderType.valueOf(orderType) }
            .getOrDefault(FloatingWordOrderType.RANDOM),
        fieldConfigs = fieldConfigs.mapNotNull { it.toDomainOrNull() }
            .ifEmpty { FloatingWordSettings.defaultFieldConfigs() },
        selectedWordIds = selectedWordIds,
        floatingBallX = floatingBallX,
        floatingBallY = floatingBallY,
        autoStartOnBoot = autoStartOnBoot,
        autoStartOnAppLaunch = autoStartOnAppLaunch,
        ballOpacityPercent = ballOpacityPercent,
        cardOpacityPercent = cardOpacityPercent,
        dockConfig = dockConfig?.toDomain() ?: FloatingDockConfig(),
        dockState = null
    )
}

private fun FloatingFieldConfigDto.toDomainOrNull(): FloatingWordFieldConfig? {
    val type = runCatching { FloatingWordFieldType.valueOf(type) }.getOrNull() ?: return null
    return FloatingWordFieldConfig(type = type, enabled = enabled, fontSizeSp = fontSizeSp)
}

private fun FloatingDockConfigDto.toDomain(): FloatingDockConfig {
    return FloatingDockConfig(
        snapTriggerDistanceDp = snapTriggerDistanceDp,
        halfHiddenEnabled = halfHiddenEnabled,
        allowedEdges = allowedEdges.mapNotNull(::parseDockEdge),
        edgePriority = edgePriority.mapNotNull(::parseDockEdge),
        snapAnimationDurationMs = snapAnimationDurationMs,
        tapExpandsCardAfterUnsnap = tapExpandsCardAfterUnsnap,
        initialDockEdge = parseDockEdge(initialDockEdge) ?: FloatingDockEdge.RIGHT
    )
}

private fun parseDockEdge(name: String): FloatingDockEdge? {
    return runCatching { FloatingDockEdge.valueOf(name) }.getOrNull()
}
