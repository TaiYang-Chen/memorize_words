package com.chen.memorizewords.domain.wordbook.model.shop
sealed class DownloadState {
    data object NotDownloaded : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    data class Paused(val progress: Int) : DownloadState()
    data object UpdateAvailable : DownloadState()
    data object Downloaded : DownloadState()
    data class Failed(val message: String) : DownloadState()
}
