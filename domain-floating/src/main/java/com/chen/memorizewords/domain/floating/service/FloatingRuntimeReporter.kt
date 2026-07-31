package com.chen.memorizewords.domain.floating.service

import com.chen.memorizewords.domain.floating.model.FloatingRuntimeEvent
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSession
import com.chen.memorizewords.domain.floating.repository.FloatingRuntimeSessionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compare-and-set writer used by both the main app process and the remote overlay process.
 * A stale service result cannot create or resurrect a session.
 */
@Singleton
class FloatingRuntimeReporter @Inject constructor(
    private val runtimeRepository: FloatingRuntimeSessionRepository
) {
    suspend fun transition(
        sessionId: String,
        expectedRevision: Long,
        event: FloatingRuntimeEvent,
        nowMs: Long = System.currentTimeMillis()
    ): FloatingRuntimeSession? {
        val snapshot = runtimeRepository.getSnapshot()
        val current = snapshot.session ?: return null
        if (current.sessionId != sessionId || current.revision != expectedRevision) return null
        if (!FloatingRuntimeReducer.canHandle(current.phase, event)) return null
        val updated = FloatingRuntimeReducer.reduce(current, event, nowMs)
        return runtimeRepository.compareAndSet(sessionId, expectedRevision, updated)
    }
}
