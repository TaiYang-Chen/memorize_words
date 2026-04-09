package com.chen.memorizewords.domain.usecase.download

import com.chen.memorizewords.domain.repository.download.DownloadRepository
import javax.inject.Inject

class PauseDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(taskId: String) {
        repository.pause(taskId)
    }
}

