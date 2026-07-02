package com.chen.memorizewords.data.study.repository

import androidx.room.withTransaction
import com.chen.memorizewords.data.study.local.StudyDatabase
import com.chen.memorizewords.data.study.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.study.local.room.model.study.progress.word.WordLearningStateEntity
import com.chen.memorizewords.data.study.local.room.model.study.progress.word.toDomain
import com.chen.memorizewords.data.study.local.room.model.study.progress.word.toEntity
import com.chen.memorizewords.domain.sync.OutboxTopic
import com.chen.memorizewords.domain.sync.SyncOperation
import com.chen.memorizewords.domain.sync.SyncOutboxWriter
import com.chen.memorizewords.domain.sync.WordStateDeleteByBookSyncPayload
import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import com.chen.memorizewords.domain.study.repository.WordLearningRepository
import com.chen.memorizewords.domain.study.repository.WordLearningStateStore
import com.google.gson.Gson
import javax.inject.Inject

class WordLearningRepositoryImpl @Inject constructor(
    private val studyDatabase: StudyDatabase,
    private val dao: WordLearningStateDao,
    private val SyncOutboxWriter: SyncOutboxWriter,    private val gson: Gson
) : WordLearningRepository, WordLearningStateStore {

    override suspend fun getLearningStatesByIds(
        wordBookId: Long,
        ids: List<Long>
    ): Map<Long, WordLearningState> {
        if (ids.isEmpty()) return emptyMap()
        val entities: List<WordLearningStateEntity> = dao.getLearningStatesByIds(wordBookId, ids)
        return entities.map { entity -> entity.toDomain() }.associateBy { it.wordId }
    }

    override suspend fun getLearningStatesByBookId(bookId: Long): List<WordLearningState> {
        return dao.getWordsByWordBookId(bookId).map { it.toDomain() }
    }

    override suspend fun getState(wordId: Long, bookId: Long): WordLearningState? {
        return dao.getState(wordId = wordId, bookId = bookId)?.toDomain()
    }

    override suspend fun upsertState(state: WordLearningState) {
        dao.upsert(state.toEntity())
    }

    override suspend fun getLearnedWordIdsByBook(bookId: Long): List<Long> {
        return dao.getLearnedWordIdsByBook(bookId)
    }

    override suspend fun deleteLearningWordByBookId(bookId: Long) {
        studyDatabase.withTransaction {
            dao.deleteLearningWordByBookId(bookId)
        }
        enqueueDeleteWordStatesByBookSync(bookId)
    }

    private suspend fun enqueueDeleteWordStatesByBookSync(bookId: Long) {
        SyncOutboxWriter.enqueueLatest(
            bizType = OutboxTopic.WORD_STATE_DELETE_BY_BOOK,
            bizKey = "word_state_delete:$bookId",
            operation = SyncOperation.DELETE,
            payload = gson.toJson(WordStateDeleteByBookSyncPayload(bookId = bookId))
        )
    }
}
