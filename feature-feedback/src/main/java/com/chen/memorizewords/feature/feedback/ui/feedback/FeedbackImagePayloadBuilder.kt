package com.chen.memorizewords.feature.feedback.ui.feedback

import com.chen.memorizewords.domain.model.feedback.FeedbackImagePayload
import java.util.Locale

internal data class FeedbackImageSource(
    val bytes: ByteArray,
    val mimeType: String,
    val originalFileName: String? = null
)

internal sealed interface FeedbackImageBuildFailure {
    data object SingleImageTooLarge : FeedbackImageBuildFailure
    data object TotalImagesTooLarge : FeedbackImageBuildFailure
}

internal sealed interface FeedbackImageBuildResult {
    data class Success(val payloads: List<FeedbackImagePayload>) : FeedbackImageBuildResult
    data class Failure(val reason: FeedbackImageBuildFailure) : FeedbackImageBuildResult
}

internal object FeedbackImagePayloadBuilder {
    const val MAX_SINGLE_IMAGE_BYTES: Int = 5 * 1024 * 1024
    const val MAX_TOTAL_IMAGE_BYTES: Int = 12 * 1024 * 1024

    fun build(
        images: List<FeedbackImageSource>,
        timestampMillis: Long
    ): FeedbackImageBuildResult {
        var totalBytes = 0L
        val payloads = images.mapIndexed { index, image ->
            if (image.bytes.size > MAX_SINGLE_IMAGE_BYTES) {
                return FeedbackImageBuildResult.Failure(
                    FeedbackImageBuildFailure.SingleImageTooLarge
                )
            }

            totalBytes += image.bytes.size.toLong()
            if (totalBytes > MAX_TOTAL_IMAGE_BYTES) {
                return FeedbackImageBuildResult.Failure(
                    FeedbackImageBuildFailure.TotalImagesTooLarge
                )
            }

            FeedbackImagePayload(
                bytes = image.bytes,
                fileName = buildFileName(image, timestampMillis, index),
                mimeType = image.mimeType.ifBlank { DEFAULT_MIME_TYPE }
            )
        }

        return FeedbackImageBuildResult.Success(payloads)
    }

    private fun buildFileName(
        image: FeedbackImageSource,
        timestampMillis: Long,
        index: Int
    ): String {
        val extension = image.originalFileName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
            ?: extensionFromMimeType(image.mimeType)
        return "feedback_${timestampMillis}_${index + 1}.$extension"
    }

    private fun extensionFromMimeType(mimeType: String): String {
        return when (mimeType.lowercase(Locale.ROOT)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
    }
}

private const val DEFAULT_MIME_TYPE = "image/jpeg"
