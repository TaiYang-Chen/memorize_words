package com.chen.memorizewords.data.repository.feedback

import com.chen.memorizewords.data.remote.RemoteResultAdapter
import com.chen.memorizewords.domain.model.feedback.FeedbackImagePayload
import com.chen.memorizewords.domain.repository.feedback.FeedbackRepository
import com.chen.memorizewords.network.api.feedback.FeedbackImageUploadRequest
import com.chen.memorizewords.network.api.feedback.FeedbackRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FeedbackRepositoryImpl @Inject constructor(
    private val feedbackRequest: FeedbackRequest,
    private val remoteResultAdapter: RemoteResultAdapter
) : FeedbackRepository {
    override suspend fun submitFeedback(
        content: String,
        contact: String?,
        images: List<FeedbackImagePayload>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        remoteResultAdapter.toResult {
            feedbackRequest.submitFeedback(
                content = content,
                contact = contact,
                images = images.map { image ->
                    FeedbackImageUploadRequest(
                        bytes = image.bytes,
                        fileName = image.fileName,
                        mimeType = image.mimeType
                    )
                }
            )
        }
    }
}
