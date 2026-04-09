package com.chen.memorizewords.domain.repository.feedback

import com.chen.memorizewords.domain.model.feedback.FeedbackImagePayload

interface FeedbackRepository {
    suspend fun submitFeedback(
        content: String,
        contact: String?,
        images: List<FeedbackImagePayload>
    ): Result<Unit>
}
