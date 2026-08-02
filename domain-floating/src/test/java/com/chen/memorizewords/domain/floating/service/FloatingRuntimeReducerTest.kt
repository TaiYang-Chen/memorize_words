package com.chen.memorizewords.domain.floating.service

import com.chen.memorizewords.domain.floating.model.FloatingRuntimeError
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeEvent
import com.chen.memorizewords.domain.floating.model.FloatingRuntimePhase
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSession
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSnapshot
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSource
import com.chen.memorizewords.domain.floating.repository.FloatingRuntimeSessionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

class FloatingRuntimeReducerTest {

    @Test
    fun `happy path reaches running only after renderer confirmation`() {
        val resolving = session(phase = FloatingRuntimePhase.RESOLVING)
        val resolved = reduce(resolving, FloatingRuntimeEvent.Resolved("green_pet"))
        val starting = reduce(resolved, FloatingRuntimeEvent.StartDispatched(30_000L))

        assertEquals(FloatingRuntimePhase.RESOLVING, resolved.phase)
        assertEquals("green_pet", resolved.targetPackId)
        assertEquals(FloatingRuntimePhase.STARTING, starting.phase)
        assertTrue(FloatingRuntimeReducer.canHandle(starting.phase, FloatingRuntimeEvent.RendererReady))

        val running = reduce(starting, FloatingRuntimeEvent.RendererReady)
        assertEquals(FloatingRuntimePhase.RUNNING, running.phase)
        assertEquals(3L, running.revision)
        assertEquals(3_000L, running.lastHeartbeatAtMs)
    }

    @Test
    fun `download and install transitions preserve the owning session`() {
        val resolved = reduce(session(), FloatingRuntimeEvent.Resolved("blue_pet"))
        val queued = reduce(resolved, FloatingRuntimeEvent.DownloadQueued())
        val downloading = reduce(queued, FloatingRuntimeEvent.DownloadProgress(43))
        val installing = reduce(downloading, FloatingRuntimeEvent.Installing)

        assertEquals(FloatingRuntimePhase.DOWNLOADING, queued.phase)
        assertEquals(43, downloading.progress)
        assertEquals(FloatingRuntimePhase.INSTALLING, installing.phase)
        assertEquals(100, installing.progress)
        assertEquals("blue_pet", installing.targetPackId)

        val ready = reduce(installing, FloatingRuntimeEvent.InstallationReady)
        assertEquals(FloatingRuntimePhase.READY_TO_START, ready.phase)
        assertEquals(5L, ready.revision)
        assertEquals(null, ready.startDeadlineAtMs)

        val starting = reduce(ready, FloatingRuntimeEvent.StartDispatched(30_000L))
        assertEquals(FloatingRuntimePhase.STARTING, starting.phase)
        assertEquals(30_000L, starting.startDeadlineAtMs)
    }

    @Test
    fun `illegal transitions are rejected instead of mutating state`() {
        assertFalse(
            FloatingRuntimeReducer.canHandle(
                FloatingRuntimePhase.RESOLVING,
                FloatingRuntimeEvent.RendererReady
            )
        )
        assertFalse(
            FloatingRuntimeReducer.canHandle(
                FloatingRuntimePhase.RUNNING,
                FloatingRuntimeEvent.DownloadProgress(10)
            )
        )
        assertFalse(
            FloatingRuntimeReducer.canHandle(
                FloatingRuntimePhase.INSTALLING,
                FloatingRuntimeEvent.DownloadProgress(10)
            )
        )
        assertFalse(
            FloatingRuntimeReducer.canHandle(
                FloatingRuntimePhase.INSTALLING,
                FloatingRuntimeEvent.StartDispatched(30_000L)
            )
        )
        assertFalse(
            FloatingRuntimeReducer.canHandle(
                FloatingRuntimePhase.FAILED,
                FloatingRuntimeEvent.Failed(FloatingRuntimeError.UNKNOWN)
            )
        )
        assertFalse(
            FloatingRuntimeReducer.canHandle(
                FloatingRuntimePhase.IDLE,
                FloatingRuntimeEvent.StopRequested
            )
        )
    }

    @Test
    fun `permission denial fails the same pending session without starting a renderer`() {
        val resolved = reduce(session(), FloatingRuntimeEvent.Resolved("green_pet"))
        val awaitingPermission = reduce(resolved, FloatingRuntimeEvent.PermissionRequired)
        val denied = reduce(
            awaitingPermission,
            FloatingRuntimeEvent.Failed(FloatingRuntimeError.PERMISSION_DENIED)
        )

        assertEquals(FloatingRuntimePhase.AWAITING_PERMISSION, awaitingPermission.phase)
        assertEquals(FloatingRuntimePhase.FAILED, denied.phase)
        assertEquals(FloatingRuntimeError.PERMISSION_DENIED, denied.error)
        assertFalse(FloatingRuntimeReducer.canHandle(denied.phase, FloatingRuntimeEvent.RendererReady))
    }

    @Test
    fun `stop clears only a stopping session`() {
        val running = session(phase = FloatingRuntimePhase.RUNNING, revision = 7L)
        val stopping = reduce(running, FloatingRuntimeEvent.StopRequested)

        assertEquals(FloatingRuntimePhase.STOPPING, stopping.phase)
        assertTrue(FloatingRuntimeReducer.canHandle(stopping.phase, FloatingRuntimeEvent.Stopped))
        assertNull(FloatingRuntimeReducer.reduce(stopping, FloatingRuntimeEvent.Stopped, NOW_MS))
        assertFalse(FloatingRuntimeReducer.canHandle(running.phase, FloatingRuntimeEvent.Stopped))
    }

    @Test
    fun `failure turns the switch off without permitting stale recovery`() {
        val starting = session(phase = FloatingRuntimePhase.STARTING, revision = 3L)
        val failed = reduce(starting, FloatingRuntimeEvent.Failed(FloatingRuntimeError.RENDER_TIMEOUT))

        assertEquals(FloatingRuntimePhase.FAILED, failed.phase)
        assertEquals(FloatingRuntimeError.RENDER_TIMEOUT, failed.error)
        assertFalse(FloatingRuntimeReducer.canHandle(failed.phase, FloatingRuntimeEvent.RendererReady))
        assertFalse(
            FloatingRuntimeReducer.canHandle(
                failed.phase,
                FloatingRuntimeEvent.Failed(FloatingRuntimeError.UNKNOWN)
            )
        )
    }

    @Test
    fun `heartbeat and reconfigure advance revision without changing running state`() {
        val running = session(phase = FloatingRuntimePhase.RUNNING, revision = 8L, configVersion = 2L)
        val reconfigured = reduce(running, FloatingRuntimeEvent.Reconfigured(3L))
        val heartbeat = reduce(reconfigured, FloatingRuntimeEvent.Heartbeat(44_000L))

        assertEquals(FloatingRuntimePhase.RUNNING, heartbeat.phase)
        assertEquals(3L, heartbeat.configVersion)
        assertEquals(44_000L, heartbeat.lastHeartbeatAtMs)
        assertEquals(10L, heartbeat.revision)
    }

    @Test
    fun `reporter compare and set rejects stale service callbacks`() = runBlocking {
        val repository = FakeRuntimeRepository(
            session(sessionId = "new-session", phase = FloatingRuntimePhase.RUNNING, revision = 9L)
        )
        val reporter = FloatingRuntimeReporter(repository)

        val stale = reporter.transition(
            sessionId = "old-session",
            expectedRevision = 2L,
            event = FloatingRuntimeEvent.Heartbeat(NOW_MS)
        )

        assertNull(stale)
        assertEquals("new-session", repository.current?.sessionId)
        assertEquals(9L, repository.current?.revision)
    }

    private fun reduce(
        session: FloatingRuntimeSession,
        event: FloatingRuntimeEvent
    ): FloatingRuntimeSession = checkNotNull(FloatingRuntimeReducer.reduce(session, event, NOW_MS))

    private fun session(
        sessionId: String = "session",
        phase: FloatingRuntimePhase = FloatingRuntimePhase.RESOLVING,
        revision: Long = 0L,
        configVersion: Long = 0L
    ) = FloatingRuntimeSession(
        sessionId = sessionId,
        revision = revision,
        phase = phase,
        source = FloatingRuntimeSource.HOME,
        configVersion = configVersion,
        createdAtMs = NOW_MS,
        updatedAtMs = NOW_MS
    )

    private class FakeRuntimeRepository(initial: FloatingRuntimeSession?) :
        FloatingRuntimeSessionRepository {
        private val state = MutableStateFlow(initial)
        val current: FloatingRuntimeSession? get() = state.value

        override fun observe(): Flow<FloatingRuntimeSnapshot> = MutableStateFlow(
            FloatingRuntimeSnapshot(state.value)
        )

        override suspend fun getSnapshot(): FloatingRuntimeSnapshot = FloatingRuntimeSnapshot(state.value)

        override suspend fun create(session: FloatingRuntimeSession): FloatingRuntimeSession {
            state.value = session
            return session
        }

        override suspend fun compareAndSet(
            sessionId: String,
            expectedRevision: Long,
            updated: FloatingRuntimeSession?
        ): FloatingRuntimeSession? {
            val current = state.value
            if (current?.sessionId != sessionId || current.revision != expectedRevision) return null
            state.value = updated
            return updated
        }

        override suspend fun clear() {
            state.value = null
        }
    }

    private companion object {
        const val NOW_MS = 3_000L
    }
}
