package com.chen.memorizewords.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.chen.memorizewords.data.local.room.AppDatabase
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.local.room.model.study.progress.word.WordLearningStateEntity
import com.chen.memorizewords.data.local.room.model.study.progress.word.toDomain
import com.chen.memorizewords.data.repository.sync.SyncOutboxBizType
import com.chen.memorizewords.data.repository.sync.SyncOutboxOperation
import com.chen.memorizewords.data.repository.sync.SyncOutboxWorkScheduler
import com.chen.memorizewords.data.repository.sync.WordStateDeleteByBookSyncPayload
import com.chen.memorizewords.data.repository.sync.syncOutboxEntity
import com.chen.memorizewords.domain.model.study.progress.word.WordLearningState
import com.chen.memorizewords.domain.repository.WordLearningRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.gson.Gson
import javax.inject.Inject

class WordLearningRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val appDatabase: AppDatabase,
    private val dao: WordLearningStateDao,
    private val syncOutboxDao: SyncOutboxDao,
    private val syncOutboxWorkScheduler: SyncOutboxWorkScheduler,
    private val gson: Gson
) : WordLearningRepository {

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

    override suspend fun getLearnedWordIdsByBook(bookId: Long): List<Long> {
        return dao.getLearnedWordIdsByBook(bookId)
    }

    override suspend fun deleteLearningWordByBookId(bookId: Long) {
        appDatabase.withTransaction {
            dao.deleteLearningWordByBookId(bookId)
        }
        enqueueDeleteWordStatesByBookSync(bookId)
    }

    private suspend fun enqueueDeleteWordStatesByBookSync(bookId: Long) {
        syncOutboxDao.upsert(
            syncOutboxEntity(
                bizType = SyncOutboxBizType.WORD_STATE_DELETE_BY_BOOK,
                bizKey = "word_state_delete:$bookId",
                operation = SyncOutboxOperation.DELETE,
                payload = gson.toJson(WordStateDeleteByBookSyncPayload(bookId = bookId))
            )
        )
        syncOutboxWorkScheduler.scheduleDrain()
    }
}
