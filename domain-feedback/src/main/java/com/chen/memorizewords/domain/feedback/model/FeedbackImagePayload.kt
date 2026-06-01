package com.chen.memorizewords.domain.feedback.model
data class FeedbackImagePayload(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String
)
