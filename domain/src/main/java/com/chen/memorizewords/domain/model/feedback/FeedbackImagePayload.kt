package com.chen.memorizewords.domain.model.feedback

data class FeedbackImagePayload(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String
)
