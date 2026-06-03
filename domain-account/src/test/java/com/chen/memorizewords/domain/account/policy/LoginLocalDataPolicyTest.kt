package com.chen.memorizewords.domain.account.policy

import com.chen.memorizewords.domain.account.model.LoginLocalDataAction
import kotlin.test.Test
import kotlin.test.assertEquals

class LoginLocalDataPolicyTest {
    private val policy = LoginLocalDataPolicy()

    @Test
    fun `keeps local data for same authenticated user`() {
        val action = policy.resolve(
            isAuthenticated = true,
            authenticatedUserId = 1L,
            retainedOwnerUserId = 1L,
            incomingUserId = 1L,
            hasUnsyncedUserData = true
        )

        assertEquals(LoginLocalDataAction.Keep, action)
    }

    @Test
    fun `blocks authenticated account switch when unsynced data exists`() {
        val action = policy.resolve(
            isAuthenticated = true,
            authenticatedUserId = 1L,
            retainedOwnerUserId = 1L,
            incomingUserId = 2L,
            hasUnsyncedUserData = true
        )

        assertEquals(LoginLocalDataAction.Block, action)
    }

    @Test
    fun `clears local data for authenticated account switch without unsynced data`() {
        val action = policy.resolve(
            isAuthenticated = true,
            authenticatedUserId = 1L,
            retainedOwnerUserId = 1L,
            incomingUserId = 2L,
            hasUnsyncedUserData = false
        )

        assertEquals(LoginLocalDataAction.Clear, action)
    }

    @Test
    fun `clears retained owner data for unauthenticated different incoming user`() {
        val action = policy.resolve(
            isAuthenticated = false,
            authenticatedUserId = null,
            retainedOwnerUserId = 1L,
            incomingUserId = 2L,
            hasUnsyncedUserData = false
        )

        assertEquals(LoginLocalDataAction.Clear, action)
    }
}

