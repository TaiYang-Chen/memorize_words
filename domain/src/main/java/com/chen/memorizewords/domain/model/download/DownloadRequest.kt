package com.chen.memorizewords.domain.model.download

data class DownloadRequest(
    val taskId: String,
    val url: String,
    val fileName: String,
    val mimeType: String = "application/octet-stream",
    val displayTitle: String = "",
    val displayDesc: String = "",
    val destinationDir: String = "",
    val completionAction: DownloadCompletionAction = DownloadCompletionAction.None
)

