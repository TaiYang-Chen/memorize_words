package com.chen.memorizewords.data.wordbook.repository.bootstrap

import androidx.room.withTransaction
import com.chen.memorizewords.data.wordbook.local.WordBookDatabase
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateEntity
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook.WordBookProgressDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook.WordBookProgressEntity
import com.chen.memorizewords.domain.wordbook.model.study.progress.wordbook.WordBookProgress
import com.chen.memorizewords.domain.wordbook.repository.WordBookLearningStateSnapshot
import com.chen.memorizewords.domain.wordbook.repository.WordBookSnapshotLocalStatePort
import javax.inject.Inject

class WordBookSnapshotLocalStateStore @Inject constructor(
    private val wordBookDatabase: WordBookDatabase,
    private val wordLearningStateDao: WordLearningStateDao,
    private val wordBookProgressDao: WordBookProgressDao
) : WordBookSnapshotLocalStatePort {

    override suspend fun overwriteLearningStatesForBookFromRemote(
        bookId: Long,
        states: List<WordBookLearningStateSnapshot>
    ) {
        wordBookDatabase.withTransaction {
            wordLearningStateDao.deleteLearningWordByBookId(bookId)
            if (states.isNotEmpty()) {
                wordLearningStateDao.upsertAll(states.map(WordBookLearningStateSnapshot::toEntity))
            }
        }
    }

    override suspend fun overwriteProgressFromRemote(progress: List<WordBookProgress>) {
        wordBookDatabase.withTransaction {
            wordBookProgressDao.deleteAll()
            if (progress.isNotEmpty()) {
                wordBookProgressDao.upsertAll(progress.map { it.toEntity() })
            }
        }
    }

    override suspend fun upsertProgressFromRemote(progress: List<WordBookProgress>) {
        if (progress.isEmpty()) return
        wordBookDatabase.withTransaction {
            wordBookProgressDao.upsertAll(progress.map { it.toEntity() })
        }
    }
}

private fun WordBookLearningStateSnapshot.toEntity(): WordLearningStateEntity {
    return WordLearningStateEntity(
        wordId = wordId,
        bookId = bookId,
        totalLearnCount = totalLearnCount,
        lastLearnedAtMs = lastLearnedAtMs,
        nextReviewAtMs = nextReviewAtMs,
        masteryLevel = masteryLevel,
        userStatus = userStatus,
        repetition = repetition,
        interval = interval,
        efactor = efactor
    )
}

private fun WordBookProgress.toEntity(): WordBookProgressEntity {
    return WordBookProgressEntity(
        wordBookId = wordBookId,
        correctCount = correctCount,
        wrongCount = wrongCount,
        studyDayCount = studyDayCount,
        lastStudyDate = lastStudyDate.ifBlank { null }
    )
}
