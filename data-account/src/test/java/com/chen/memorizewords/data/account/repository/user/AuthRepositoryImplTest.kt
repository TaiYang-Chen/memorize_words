package com.chen.memorizewords.data.account.repository.user

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryImplTest {

    @Test
    fun `resolveLoginLocalDataAction returns clear when logged out and retained owner differs`() {
        assertEquals(
            LoginLocalDataAction.Clear,
            resolveLoginLocalDataAction(
                isAuthenticated = false,
                authenticatedUserId = null,
                retainedOwnerUserId = 1L,
                incomingUserId = 2L,
                hasUnsyncedUserData = true
            )
        )
    }

    @Test
    fun `resolveLoginLocalDataAction returns keep when logged out and retained owner matches`() {
        assertEquals(
            LoginLocalDataAction.Keep,
            resolveLoginLocalDataAction(
                isAuthenticated = false,
                authenticatedUserId = null,
                retainedOwnerUserId = 2L,
                incomingUserId = 2L,
                hasUnsyncedUserData = true
            )
        )
    }

    @Test
    fun `resolveLoginLocalDataAction returns block when authenticated account switch still has pending data`() {
        assertEquals(
            LoginLocalDataAction.Block,
            resolveLoginLocalDataAction(
                isAuthenticated = true,
                authenticatedUserId = 1L,
                retainedOwnerUserId = 1L,
                incomingUserId = 2L,
                hasUnsyncedUserData = true
            )
        )
    }

    @Test
    fun `resolveLoginLocalDataAction returns clear when authenticated account switch has no pending data`() {
        assertEquals(
            LoginLocalDataAction.Clear,
            resolveLoginLocalDataAction(
                isAuthenticated = true,
                authenticatedUserId = 1L,
                retainedOwnerUserId = 1L,
                incomingUserId = 2L,
                hasUnsyncedUserData = false
            )
        )
    }

    @Test
    fun `shouldAbortLogoutAfterFlush returns false when force logout is requested`() {
        assertFalse(
            shouldAbortLogoutAfterFlush(
                force = true,
                pendingSyncCount = 3
            )
        )
    }

    @Test
    fun `shouldAbortLogoutAfterFlush returns false when no pending sync data remains`() {
        assertFalse(
            shouldAbortLogoutAfterFlush(
                force = false,
                pendingSyncCount = 0
            )
        )
    }

    @Test
    fun `shouldAbortLogoutAfterFlush returns true when normal logout still has pending data`() {
        assertTrue(
            shouldAbortLogoutAfterFlush(
                force = false,
                pendingSyncCount = 2
            )
        )
    }
}
