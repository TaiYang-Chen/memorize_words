package com.chen.memorizewords.data.floating.repository

import androidx.room.withTransaction
import com.chen.memorizewords.data.floating.local.FloatingDatabase
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingRuntimeSessionEntity
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingRuntimeSessionDao
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeError
import com.chen.memorizewords.domain.floating.model.FloatingRuntimePhase
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSession
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSnapshot
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSource
import com.chen.memorizewords.domain.floating.repository.FloatingRuntimeSessionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FloatingRuntimeSessionRepositoryImpl @Inject constructor(
    private val database: FloatingDatabase,
    private val dao: FloatingRuntimeSessionDao
) : FloatingRuntimeSessionRepository {

    override fun observe(): Flow<FloatingRuntimeSnapshot> = dao.observe().map { entity ->
        FloatingRuntimeSnapshot(entity?.toDomain())
    }

    override suspend fun getSnapshot(): FloatingRuntimeSnapshot =
        FloatingRuntimeSnapshot(dao.get()?.toDomain())

    override suspend fun create(session: FloatingRuntimeSession): FloatingRuntimeSession {
        database.withTransaction {
            dao.upsert(session.toEntity())
        }
        return session
    }

    override suspend fun compareAndSet(
        sessionId: String,
        expectedRevision: Long,
        updated: FloatingRuntimeSession?
    ): FloatingRuntimeSession? = database.withTransaction {
        val current = dao.get() ?: return@withTransaction null
        if (current.sessionId != sessionId || current.revision != expectedRevision) {
            return@withTransaction null
        }
        if (updated == null) {
            dao.clear()
            null
        } else {
            dao.upsert(updated.toEntity())
            updated
        }
    }

    override suspend fun clear() {
        database.withTransaction { dao.clear() }
    }
}

private fun FloatingRuntimeSessionEntity.toDomain(): FloatingRuntimeSession = FloatingRuntimeSession(
    sessionId = sessionId,
    revision = revision,
    phase = runCatching { FloatingRuntimePhase.valueOf(phase) }
        .getOrDefault(FloatingRuntimePhase.FAILED),
    source = runCatching { FloatingRuntimeSource.valueOf(source) }
        .getOrDefault(FloatingRuntimeSource.HOME),
    targetPackId = targetPackId,
    progress = progress.coerceIn(0, 100),
    error = errorCode?.let { value ->
        runCatching { FloatingRuntimeError.valueOf(value) }.getOrNull()
    },
    startDeadlineAtMs = startDeadlineAtMs,
    lastHeartbeatAtMs = lastHeartbeatAtMs,
    configVersion = configVersion,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs
)

private fun FloatingRuntimeSession.toEntity(): FloatingRuntimeSessionEntity = FloatingRuntimeSessionEntity(
    sessionId = sessionId,
    revision = revision,
    phase = phase.name,
    source = source.name,
    targetPackId = targetPackId,
    progress = progress.coerceIn(0, 100),
    errorCode = error?.name,
    startDeadlineAtMs = startDeadlineAtMs,
    lastHeartbeatAtMs = lastHeartbeatAtMs,
    configVersion = configVersion,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs
)
