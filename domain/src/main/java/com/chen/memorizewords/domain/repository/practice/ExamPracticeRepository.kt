package com.chen.memorizewords.domain.repository.practice

import com.chen.memorizewords.domain.model.practice.ExamItemState
import com.chen.memorizewords.domain.model.practice.ExamPracticeSessionSubmission
import com.chen.memorizewords.domain.model.practice.ExamPracticeWord

interface ExamPracticeRepository {
    suspend fun getWordPractice(wordId: Long): Result<ExamPracticeWord>

    suspend fun updateFavorite(itemId: Long, favorite: Boolean): Result<ExamItemState>

    suspend fun submitSession(submission: ExamPracticeSessionSubmission): Result<Unit>
}
