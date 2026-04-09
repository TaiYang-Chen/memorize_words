package com.chen.memorizewords.domain.usecase.download

import com.chen.memorizewords.domain.repository.download.DownloadRepository
import javax.inject.Inject

class ResumeDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(taskId: String) {
        repository.resume(taskId)
    }
}

