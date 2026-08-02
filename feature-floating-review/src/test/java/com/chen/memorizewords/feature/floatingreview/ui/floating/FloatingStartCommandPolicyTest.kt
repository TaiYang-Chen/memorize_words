package com.chen.memorizewords.feature.floatingreview.ui.floating

import com.chen.memorizewords.domain.floating.model.FloatingRuntimePhase
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSession
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSource
import kotlin.test.Test
import kotlin.test.assertEquals

class FloatingStartCommandPolicyTest {

    @Test
    fun `late start after renderer is running is ignored`() {
        val action = resolveFloatingStartCommandAction(
            runtimeSession = session(phase = FloatingRuntimePhase.RUNNING, revision = 8L),
            commandSessionId = SESSION_ID,
            commandRevision = 7L,
            activeSessionId = SESSION_ID,
            activeRevision = 8L,
            pendingSessionId = null,
            pendingRevision = null
        )

        assertEquals(FloatingStartCommandAction.IGNORE, action)
    }

    @Test
    fun `duplicate start for an in-flight session is ignored`() {
        val action = resolveFloatingStartCommandAction(
            runtimeSession = session(phase = FloatingRuntimePhase.STARTING, revision = 7L),
            commandSessionId = SESSION_ID,
            commandRevision = 7L,
            activeSessionId = null,
            activeRevision = null,
            pendingSessionId = SESSION_ID,
            pendingRevision = 7L
        )

        assertEquals(FloatingStartCommandAction.IGNORE, action)
    }

    @Test
    fun `only the current starting session may replace an older surface`() {
        val action = resolveFloatingStartCommandAction(
            runtimeSession = session(sessionId = "new", phase = FloatingRuntimePhase.STARTING, revision = 4L),
            commandSessionId = "new",
            commandRevision = 4L,
            activeSessionId = SESSION_ID,
            activeRevision = 7L,
            pendingSessionId = null,
            pendingRevision = null
        )

        assertEquals(FloatingStartCommandAction.START, action)
    }

    private fun session(
        sessionId: String = SESSION_ID,
        phase: FloatingRuntimePhase,
        revision: Long
    ) = FloatingRuntimeSession(
        sessionId = sessionId,
        revision = revision,
        phase = phase,
        source = FloatingRuntimeSource.HOME,
        createdAtMs = 1L,
        updatedAtMs = 1L
    )

    private companion object {
        const val SESSION_ID = "session"
    }
}
