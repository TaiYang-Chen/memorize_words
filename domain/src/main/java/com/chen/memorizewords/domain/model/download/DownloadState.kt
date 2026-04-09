package com.chen.memorizewords.domain.model.download

data class DownloadState(
    val taskId: String,
    val status: DownloadStatus = DownloadStatus.Idle,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val filePath: String? = null,
    val errorMessage: String? = null
)

