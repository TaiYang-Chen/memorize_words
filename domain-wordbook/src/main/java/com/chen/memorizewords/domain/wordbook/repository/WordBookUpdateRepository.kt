package com.chen.memorizewords.domain.wordbook.repository
import com.chen.memorizewords.domain.wordbook.model.WordBookPendingUpdate
import com.chen.memorizewords.domain.wordbook.model.WordBookUpdateAction
import com.chen.memorizewords.domain.wordbook.model.WordBookUpdateCandidate
import com.chen.memorizewords.domain.wordbook.model.WordBookUpdateExecutionMode
import com.chen.memorizewords.domain.wordbook.model.WordBookUpdateJobState
import com.chen.memorizewords.domain.wordbook.model.WordBookUpdateSettings
import com.chen.memorizewords.domain.wordbook.model.WordBookUpdateTrigger
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
