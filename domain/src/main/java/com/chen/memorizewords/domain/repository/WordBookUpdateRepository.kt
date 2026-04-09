package com.chen.memorizewords.domain.repository

import com.chen.memorizewords.domain.model.wordbook.WordBookPendingUpdate
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateAction
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateCandidate
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateExecutionMode
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateJobState
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateSettings
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateTrigger
import kotlinx.coroutines.flow.Flow

interface WordBookUpdateRepository {
    suspend fun fetchCandidate(trigger: WordBookUpdateTrigger): Result<WordBookUpdateCandidate?>
    suspend fun reportAction(
        action: WordBookUpdateAction,
        candidate: WordBookUpdateCandidate? = null,
        trigger: WordBookUpdateTrigger? = null,
        executionMode: WordBookUpdateExecutionMode? = null,
        message: String? = null,
        deferredUntil: Long? = null
    ): Result<Unit>

    suspend fun saveDeferredUntil(bookId: Long, deferredUntil: Long)
    suspend fun ignoreVersion(bookId: Long, targetVersion: Long): Result<Unit>
    suspend fun enqueueUpdate(
        bookId: Long,
        targetVersion: Long,
        executionMode: WordBookUpdateExecutionMode
    )

    suspend fun getSettings(): WordBookUpdateSettings
    fun observeSettings(): Flow<WordBookUpdateSettings>
    suspend fun saveSettings(settings: WordBookUpdateSettings)
    fun observeUpdateJobState(bookId: Long): Flow<WordBookUpdateJobState>

    // Legacy methods kept temporarily while older use cases are being phased out.
    suspend fun getPendingUpdate(bookId: Long): Result<WordBookPendingUpdate?>
    suspend fun ignoreUpdate(bookId: Long, targetVersion: Long): Result<Unit>
    suspend fun markPrompted(bookId: Long, targetVersion: Long)
}
