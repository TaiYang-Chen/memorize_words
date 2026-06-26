package com.chen.memorizewords.core.ui.image

import android.widget.ImageView
import androidx.core.view.isVisible
import coil.Coil
import coil.request.ImageRequest

object WordBookCoverImageLoader {

    fun load(imageView: ImageView, fallbackView: ImageView, rawUrl: String?) {
        val displayUrl = WordBookCoverUrlNormalizer.toDisplayUrl(rawUrl)
        if (displayUrl == null) {
            imageView.setImageDrawable(null)
            imageView.isVisible = false
            fallbackView.isVisible = true
            return
        }

        fallbackView.isVisible = true
        imageView.isVisible = false

        val request = ImageRequest.Builder(imageView.context)
            .data(displayUrl)
            .crossfade(true)
            .target(
                onStart = {
                    imageView.setImageDrawable(null)
                    imageView.isVisible = false
                    fallbackView.isVisible = true
                },
                onSuccess = { result ->
                    imageView.setImageDrawable(result)
                    imageView.isVisible = true
                    fallbackView.isVisible = false
                },
                onError = {
                    imageView.setImageDrawable(null)
                    imageView.isVisible = false
                    fallbackView.isVisible = true
                }
            )
            .build()

        Coil.imageLoader(imageView.context).enqueue(request)
    }
}

object WordBookCoverUrlNormalizer {
    private const val LOCALHOST = "localhost"
    private const val EMULATOR_HOST = "10.0.2.2"
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8080"

    @JvmStatic
    fun toDisplayUrl(rawUrl: String?): String? {
        val raw = rawUrl?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val lower = raw.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> {
                raw.replace("http://$LOCALHOST", "http://$EMULATOR_HOST")
                    .replace("https://$LOCALHOST", "https://$EMULATOR_HOST")
            }

            raw.startsWith("/") -> "$DEFAULT_BASE_URL$raw"
            else -> "$DEFAULT_BASE_URL/$raw"
        }
    }
}
