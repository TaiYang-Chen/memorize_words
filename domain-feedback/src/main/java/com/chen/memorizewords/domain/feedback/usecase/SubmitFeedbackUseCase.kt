package com.chen.memorizewords.domain.feedback.usecase
import com.chen.memorizewords.domain.feedback.model.FeedbackImagePayload
import com.chen.memorizewords.domain.feedback.repository.FeedbackRepository
import javax.inject.Inject

class SubmitFeedbackUseCase @Inject constructor(
    private val repository: FeedbackRepository
) {
    suspend operator fun invoke(
        content: String,
        contact: String?,
        images: List<FeedbackImagePayload>
    ): Result<Unit> {
        if (content.isBlank()) {
            return Result.failure(IllegalArgumentException("Feedback content is required"))
        }
        if (images.size > 3) {
            return Result.failure(IllegalArgumentException("Up to 3 images are allowed"))
        }
        return repository.submitFeedback(content.trim(), contact?.trim(), images)
    }
}
