package com.chen.memorizewords.feature.floatingreview.ui.floating.pet

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingPetReloadPolicyTest {
    @Test
    fun `same installed version reuses the active session for a normal switch`() {
        assertTrue(
            shouldReuseFloatingPetSession(
                forceReload = false,
                hasCurrentSession = true,
                sameView = true,
                samePackId = true,
                samePackVersion = true
            )
        )
    }

    @Test
    fun `forced reload replaces the active session even for the same version`() {
        assertFalse(
            shouldReuseFloatingPetSession(
                forceReload = true,
                hasCurrentSession = true,
                sameView = true,
                samePackId = true,
                samePackVersion = true
            )
        )
    }
}
