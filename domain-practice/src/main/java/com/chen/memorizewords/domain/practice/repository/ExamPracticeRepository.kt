package com.chen.memorizewords.domain.practice.repository
import com.chen.memorizewords.domain.practice.model.ExamItemState
import com.chen.memorizewords.domain.practice.model.ExamPracticeSessionSubmission
import com.chen.memorizewords.domain.practice.model.ExamPracticeWord

interface ExamPracticeRepository {
    suspend fun getWordPractice(wordId: Long): Result<ExamPracticeWord>

    suspend fun updateFavorite(itemId: Long, favorite: Boolean): Result<ExamItemState>

    suspend fun submitSession(submission: ExamPracticeSessionSubmission): Result<Unit>
}
