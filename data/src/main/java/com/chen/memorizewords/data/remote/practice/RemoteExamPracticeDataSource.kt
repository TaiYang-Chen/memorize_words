package com.chen.memorizewords.data.remote.practice

import com.chen.memorizewords.network.api.practice.ExamItemStateDto
import com.chen.memorizewords.network.api.practice.ExamPracticeSessionSubmitRequest
import com.chen.memorizewords.network.api.practice.ExamPracticeWordResponseDto

interface RemoteExamPracticeDataSource {
    suspend fun getWordPractice(wordId: Long): Result<ExamPracticeWordResponseDto>

    suspend fun updateFavorite(itemId: Long, favorite: Boolean): Result<ExamItemStateDto>

    suspend fun submitSession(request: ExamPracticeSessionSubmitRequest): Result<Unit>
}
