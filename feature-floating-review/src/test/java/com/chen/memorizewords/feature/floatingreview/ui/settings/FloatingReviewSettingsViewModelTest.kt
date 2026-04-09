package com.chen.memorizewords.feature.floatingreview.ui.settings

import com.chen.memorizewords.domain.model.floating.FloatingWordOrderType
import com.chen.memorizewords.domain.model.floating.FloatingWordSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingReviewSettingsViewModelTest {

    @Test
    fun `opacity-only change does not require floating content refresh`() {
        val previous = FloatingWordSettings(cardOpacityPercent = 100)
        val updated = previous.copy(cardOpacityPercent = 50)

        assertFalse(shouldRefreshFloatingContent(previous, updated))
    }

    @Test
    fun `order change still requires floating content refresh`() {
        val previous = FloatingWordSettings(orderType = FloatingWordOrderType.RANDOM)
        val updated = previous.copy(orderType = FloatingWordOrderType.LENGTH_ASC)

        assertTrue(shouldRefreshFloatingContent(previous, updated))
    }
}
