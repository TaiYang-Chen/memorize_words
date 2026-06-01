package com.chen.memorizewords.domain.wordbook.usecase.download
import com.chen.memorizewords.domain.wordbook.model.download.DownloadRequest
import com.chen.memorizewords.domain.wordbook.repository.download.DownloadRepository
import javax.inject.Inject

class StartDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(request: DownloadRequest) {
        repository.start(request)
    }
}

