package com.chen.memorizewords.domain.repository.download

import com.chen.memorizewords.domain.model.download.DownloadRequest
import com.chen.memorizewords.domain.model.download.DownloadState
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    suspend fun start(request: DownloadRequest)
    suspend fun pause(taskId: String)
    suspend fun resume(taskId: String)
    suspend fun cancel(taskId: String)
    fun observeState(taskId: String): Flow<DownloadState>
}

