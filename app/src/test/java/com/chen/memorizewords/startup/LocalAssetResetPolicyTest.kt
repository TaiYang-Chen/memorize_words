package com.chen.memorizewords.startup

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalAssetResetPolicyTest {

    @Test
    fun `policy keeps account keys needed to preserve login state`() {
        val policy = LocalAssetResetPolicy()

        assertContains(policy.preservedStringKeys, "key_access_token")
        assertContains(policy.preservedStringKeys, "key_refresh_token")
        assertContains(policy.preservedLongKeys, "key_expires_at")
        assertContains(policy.preservedLongKeys, "user_id")
    }

    @Test
    fun `policy resets only older applied versions`() {
        val policy = LocalAssetResetPolicy(resetVersion = 2)

        assertTrue(policy.shouldReset(appliedVersion = 0))
        assertTrue(policy.shouldReset(appliedVersion = 1))
        assertFalse(policy.shouldReset(appliedVersion = 2))
    }
}
