package com.chen.memorizewords.feature.onboarding

import android.widget.ImageView
import androidx.core.view.isVisible
import coil.Coil
import coil.request.ImageRequest

object OnboardingWordBookImageLoader {

    fun load(imageView: ImageView, fallbackView: ImageView, rawUrl: String?) {
        val displayUrl = toDisplayUrl(rawUrl)
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

    private fun toDisplayUrl(rawUrl: String?): String? {
        val raw = rawUrl?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val lower = raw.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> {
                raw.replace("http://localhost", "http://10.0.2.2")
                    .replace("https://localhost", "https://10.0.2.2")
            }

            raw.startsWith("/") -> "http://10.0.2.2:8080$raw"
            else -> "http://10.0.2.2:8080/$raw"
        }
    }
}
