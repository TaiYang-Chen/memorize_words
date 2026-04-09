package com.chen.memorizewords.domain.usecase.download

import com.chen.memorizewords.domain.model.download.DownloadState
import com.chen.memorizewords.domain.repository.download.DownloadRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDownloadStateUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    operator fun invoke(taskId: String): Flow<DownloadState> {
        return repository.observeState(taskId)
    }
}

