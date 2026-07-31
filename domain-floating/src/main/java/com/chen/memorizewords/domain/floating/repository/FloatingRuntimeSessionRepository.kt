package com.chen.memorizewords.domain.floating.repository

import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSession
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSnapshot
import kotlinx.coroutines.flow.Flow

/** Durable, cross-process source of truth for the one active floating runtime session. */
interface FloatingRuntimeSessionRepository {
    fun observe(): Flow<FloatingRuntimeSnapshot>
    suspend fun getSnapshot(): FloatingRuntimeSnapshot
    suspend fun create(session: FloatingRuntimeSession): FloatingRuntimeSession

    /** Returns null when another process has already replaced or advanced this session. */
    suspend fun compareAndSet(
        sessionId: String,
        expectedRevision: Long,
        updated: FloatingRuntimeSession?
    ): FloatingRuntimeSession?

    suspend fun clear()
}
