package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.domain.account.auth.AuthStateProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

class FailureQueueSessionGateTest {

    @Test
    fun `request finishing after session reset cannot commit`() = runBlocking {
        val auth = MutableAuthStateProvider(true)
        val gate = FailureQueueSessionGate(auth)
        val token = requireNotNull(gate.capture())

        gate.invalidateAndRun { }

        assertFalse(gate.isCurrent(token))
        assertNull(gate.withCurrentSession(token) { "saved" })
    }

    @Test
    fun `reset waits for active commit then clears it`() = runBlocking {
        val auth = MutableAuthStateProvider(true)
        val gate = FailureQueueSessionGate(auth)
        val token = requireNotNull(gate.capture())
        val commitStarted = CompletableDeferred<Unit>()
        val allowCommit = CompletableDeferred<Unit>()
        val operations = mutableListOf<String>()

        val commit = async {
            gate.withCurrentSession(token) {
                commitStarted.complete(Unit)
                allowCommit.await()
                operations += "save"
            }
        }
        commitStarted.await()
        val reset = async {
            gate.invalidateAndRun { operations += "clear" }
        }
        allowCommit.complete(Unit)
        commit.await()
        reset.await()

        assertEquals(listOf("save", "clear"), operations)
        assertFalse(gate.isCurrent(token))
    }

    @Test
    fun `unauthenticated session cannot capture queue token`() {
        val auth = MutableAuthStateProvider(false)

        assertNull(FailureQueueSessionGate(auth).capture())

        auth.authenticated.value = true
        assertTrue(FailureQueueSessionGate(auth).capture() != null)
    }
}

private class MutableAuthStateProvider(initial: Boolean) : AuthStateProvider {
    val authenticated = MutableStateFlow(initial)

    override fun isAuthenticated(): Boolean = authenticated.value

    override fun observeAuthenticated(): Flow<Boolean> = authenticated
}
