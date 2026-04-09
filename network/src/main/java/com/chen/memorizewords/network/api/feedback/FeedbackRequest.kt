package com.chen.memorizewords.network.api.feedback

import com.chen.memorizewords.network.api.NetworkRequestExecutor
import com.chen.memorizewords.network.model.ApiResponse
import com.chen.memorizewords.network.util.NetworkResult
import com.chen.memorizewords.network.util.await
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

data class FeedbackImageUploadRequest(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String
)

@Singleton
class FeedbackRequest @Inject constructor(
    private val feedbackApiService: FeedbackApiService,
    private val requestExecutor: NetworkRequestExecutor
) {
    suspend fun submitFeedback(
        content: String,
        contact: String?,
        images: List<FeedbackImageUploadRequest>
    ): NetworkResult<Unit> = requestExecutor.executeAuthenticated {
        val contentBody = content.toRequestBody("text/plain".toMediaTypeOrNull())
        val contactBody = contact
            ?.takeIf { it.isNotBlank() }
            ?.toRequestBody("text/plain".toMediaTypeOrNull())
        val imageParts = images.mapIndexed { index, image ->
            val mediaType = image.mimeType.toMediaTypeOrNull() ?: "image/*".toMediaTypeOrNull()
            val body: RequestBody = image.bytes.toRequestBody(mediaType)
            MultipartBody.Part.createFormData(
                name = "images",
                filename = image.fileName.ifBlank { "feedback_${index + 1}.jpg" },
                body = body
            )
        }

        feedbackApiService.submitFeedback(
            content = contentBody,
            contact = contactBody,
            images = imageParts
        ).await<ApiResponse<Unit>, Unit>()
    }
}
