package com.chen.memorizewords.feature.wordbook

import com.chen.memorizewords.core.navigation.WordBookEntryDestination
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal data class WordBookLaunchTarget(
    val destination: Destination,
    val source: String? = null
) {
    internal enum class Destination {
        FAVORITES,
        MY_WORD_BOOKS,
        SHOP
    }
}

internal object WordBookDeepLinkResolver {

    fun resolve(dataString: String?): WordBookLaunchTarget? {
        val normalized = dataString?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        if (!uri.scheme.equals("myapp", ignoreCase = true)) return null

        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path.orEmpty().trimEnd('/')

        return when {
            host == "favorites" && path.isEmpty() -> {
                WordBookLaunchTarget(WordBookLaunchTarget.Destination.FAVORITES)
            }

            host == "wordbook" && path == "/my-books" -> {
                WordBookLaunchTarget(
                    destination = WordBookLaunchTarget.Destination.MY_WORD_BOOKS,
                    source = extractQueryParam(uri.rawQuery, "source")
                )
            }

            host == "wordbook" && path == "/shop" -> {
                WordBookLaunchTarget(WordBookLaunchTarget.Destination.SHOP)
            }

            normalized == WordBookEntryDestination.FAVORITES_DEEP_LINK -> {
                WordBookLaunchTarget(WordBookLaunchTarget.Destination.FAVORITES)
            }

            normalized == WordBookEntryDestination.MY_BOOKS_DEEP_LINK -> {
                WordBookLaunchTarget(WordBookLaunchTarget.Destination.MY_WORD_BOOKS)
            }

            normalized == WordBookEntryDestination.SHOP_DEEP_LINK -> {
                WordBookLaunchTarget(WordBookLaunchTarget.Destination.SHOP)
            }

            else -> null
        }
    }

    private fun extractQueryParam(rawQuery: String?, key: String): String? {
        if (rawQuery.isNullOrBlank()) return null
        return rawQuery
            .split("&")
            .asSequence()
            .mapNotNull { segment ->
                val parts = segment.split("=", limit = 2)
                val name = decode(parts[0])
                if (name != key) {
                    null
                } else {
                    decode(parts.getOrElse(1) { "" }).takeIf { it.isNotEmpty() }
                }
            }
            .firstOrNull()
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }
}
