package com.chen.memorizewords.domain.usecase.download

import com.chen.memorizewords.domain.model.download.DownloadRequest
import com.chen.memorizewords.domain.repository.download.DownloadRepository
import javax.inject.Inject

class StartDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(request: DownloadRequest) {
        repository.start(request)
    }
}

