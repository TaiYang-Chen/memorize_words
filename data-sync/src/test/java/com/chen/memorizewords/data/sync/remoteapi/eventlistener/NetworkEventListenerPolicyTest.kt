package com.chen.memorizewords.data.sync.remoteapi.eventlistener

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkEventListenerPolicyTest {

    @Test
    fun `network lifecycle logging is disabled when network logging switch is false`() {
        assertFalse(
            shouldEnableNetworkEventLogging(
                isDebug = true,
                enableNetworkLogging = false
            )
        )
    }

    @Test
    fun `network lifecycle logging requires debug build and enabled switch`() {
        assertFalse(
            shouldEnableNetworkEventLogging(
                isDebug = false,
                enableNetworkLogging = true
            )
        )
        assertTrue(
            shouldEnableNetworkEventLogging(
                isDebug = true,
                enableNetworkLogging = true
            )
        )
    }
}
