package com.chen.memorizewords.domain.wordbook.usecase.download
import com.chen.memorizewords.domain.wordbook.repository.download.DownloadRepository
import javax.inject.Inject

class CancelDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(taskId: String) {
        repository.cancel(taskId)
    }
}

