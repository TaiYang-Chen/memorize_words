package com.chen.memorizewords.domain.feedback.repository
import com.chen.memorizewords.domain.feedback.model.FeedbackImagePayload

interface FeedbackRepository {
    suspend fun submitFeedback(
        content: String,
        contact: String?,
        images: List<FeedbackImagePayload>
    ): Result<Unit>
}
