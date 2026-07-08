package com.chen.memorizewords.data.wordbook.repository.learning

import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateEntity
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.toDomain
import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import com.chen.memorizewords.domain.study.repository.WordLearningRepository
import com.chen.memorizewords.domain.study.repository.WordLearningStateStore
import javax.inject.Inject

class WordLearningStateRepositoryImpl @Inject constructor(
    private val dao: WordLearningStateDao
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

    override suspend fun getLearnedWordIdsByBook(bookId: Long): List<Long> {
        return dao.getLearnedWordIdsByBook(bookId)
    }

    override suspend fun getState(wordId: Long, bookId: Long): WordLearningState? {
        return dao.getState(wordId = wordId, bookId = bookId)?.toDomain()
    }
}
