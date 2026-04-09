package com.chen.memorizewords.data.remote.practice

import com.chen.memorizewords.data.remote.RemoteResultAdapter
import com.chen.memorizewords.network.api.practice.ExamItemStateDto
import com.chen.memorizewords.network.api.practice.ExamPracticeRequest
import com.chen.memorizewords.network.api.practice.ExamPracticeSessionSubmitRequest
import com.chen.memorizewords.network.api.practice.ExamPracticeWordResponseDto
import javax.inject.Inject

class RemoteExamPracticeDataSourceImpl @Inject constructor(
    private val request: ExamPracticeRequest,
    private val remoteResultAdapter: RemoteResultAdapter
) : RemoteExamPracticeDataSource {

    override suspend fun getWordPractice(wordId: Long): Result<ExamPracticeWordResponseDto> {
        return remoteResultAdapter.toResult { request.getWordPractice(wordId) }
    }

    override suspend fun updateFavorite(itemId: Long, favorite: Boolean): Result<ExamItemStateDto> {
        return remoteResultAdapter.toResult { request.updateFavorite(itemId, favorite) }
    }

    override suspend fun submitSession(request: ExamPracticeSessionSubmitRequest): Result<Unit> {
        return remoteResultAdapter.toResult { this.request.submitSession(request) }
    }
}
