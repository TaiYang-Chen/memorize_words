package com.chen.memorizewords.feature.wordbook.shop

import com.chen.memorizewords.domain.model.wordbook.WordBook
import com.chen.memorizewords.domain.model.wordbook.shop.DownloadState

data class BookShopUi(
    val book: WordBook,
    val downloadState: DownloadState
) {
    val progress: Int
        get() = when (downloadState) {
            is DownloadState.Downloading -> downloadState.progress
            is DownloadState.Paused -> downloadState.progress
            is DownloadState.Downloaded,
            is DownloadState.UpdateAvailable -> 100
            else -> 0
        }

    val actionText: String
        get() = when (downloadState) {
            is DownloadState.NotDownloaded -> "下载"
            is DownloadState.Downloading -> "暂停 ${downloadState.progress}%"
            is DownloadState.Paused -> "继续 ${downloadState.progress}%"
            is DownloadState.Downloaded,
            is DownloadState.UpdateAvailable -> "已下载"
            is DownloadState.Failed -> "重试"
        }

    val actionEnabled: Boolean
        get() = when (downloadState) {
            is DownloadState.Downloaded,
            is DownloadState.UpdateAvailable -> false
            else -> true
        }

    val actionProgressPercent: Int
        get() = progress.coerceIn(0, 100)
}
