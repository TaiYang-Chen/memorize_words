package com.chen.memorizewords.data.repository

import com.chen.memorizewords.data.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.local.room.model.study.progress.word.toDomain
import com.chen.memorizewords.data.local.room.model.study.progress.wordbook.WordBookProgressDao
import com.chen.memorizewords.data.local.room.model.wordbook.wordbook.WordBookDao
import com.chen.memorizewords.data.model.study.progress.wordbook.toDomain
import com.chen.memorizewords.domain.model.study.progress.wordbook.WordBookProgress
import com.chen.memorizewords.domain.repository.LearningProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LearningProgressRepositoryImpl @Inject constructor(
    private val wordBookDao: WordBookDao,
    private val learningStateDao: WordLearningStateDao,
    private val wordBookProgressDao: WordBookProgressDao
) : LearningProgressRepository {

    override fun getProgressForBooksFlow(bookIds: List<Long>): Flow<Map<Long, WordBookProgress>> {
        val booksFlow = wordBookDao.getAllWordBooksFlow()
            .map { books -> books.filter { it.id in bookIds } }

        val idsFlow = booksFlow
            .map { books -> books.map { it.id } }
            .distinctUntilChanged()

        val progressFlow = idsFlow.flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(emptyList())
            } else {
                wordBookProgressDao.getWordBooksProgress(ids)
            }
        }

        return combine(booksFlow, progressFlow) { books, progressEntities ->
            val progressMap = progressEntities.associateBy { it.wordBookId }
            books.associate { book ->
                val progress = progressMap[book.id]?.toDomain()?.copy(
                    wordBookName = book.title,
                    totalCount = book.totalWords
                ) ?: WordBookProgress(
                    wordBookId = book.id,
                    wordBookName = book.title,
                    learningCount = 0,
                    masteredCount = 0,
                    totalCount = book.totalWords,
                    correctCount = 0,
                    wrongCount = 0,
                    studyDayCount = 0,
                    lastStudyDate = ""
                )
                book.id to progress
            }
        }
    }

    override fun getProgressByWordBookId(bookId: Long): Flow<WordBookProgress?> {
        val bookFlow = wordBookDao.getAllWordBooksFlow()
            .map { list -> list.firstOrNull { it.id == bookId } }
            .distinctUntilChanged()
        val progressFlow = wordBookProgressDao.getWordBookProgress(bookId)
        return combine(bookFlow, progressFlow) { book, progress ->
            if (book == null || progress == null) return@combine null
            progress.toDomain().copy(
                wordBookName = book.title,
                totalCount = book.totalWords
            )
        }
    }

    override fun getStudyTotalWordCount(): Flow<Int> {
        return learningStateDao.getStudyTotalWordCount()
    }
}
