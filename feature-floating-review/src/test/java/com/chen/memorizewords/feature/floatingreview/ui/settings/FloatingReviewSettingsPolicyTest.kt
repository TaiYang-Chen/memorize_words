package com.chen.memorizewords.feature.floatingreview.ui.settings

import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import kotlin.test.Test
import kotlin.test.assertFalse

class FloatingReviewSettingsPolicyTest {

    @Test
    fun `changing card gap does not refresh floating content`() {
        val previous = FloatingWordSettings(cardGapDp = 40)
        val updated = previous.copy(cardGapDp = 24)

        assertFalse(shouldRefreshFloatingContent(previous, updated))
    }
}
