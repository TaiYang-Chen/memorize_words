package com.chen.memorizewords.domain.feedback
data class FeedbackDraft(
    val content: String,
    val contact: String?,
    val imageBytes: List<ByteArray> = emptyList()
)

interface FeedbackRepository {
    suspend fun submit(draft: FeedbackDraft): Result<Unit>
}
