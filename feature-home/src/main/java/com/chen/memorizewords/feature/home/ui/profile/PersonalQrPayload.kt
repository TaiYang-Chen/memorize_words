package com.chen.memorizewords.feature.home.ui.profile

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object PersonalQrPayload {
    private const val SCHEME = "myapp"
    private const val HOST = "user"
    private const val PATH = "/card"
    private const val QUERY_UID = "uid"
    private const val QUERY_NAME = "name"

    fun create(userId: Long, nickname: String?): String {
        val safeName = nickname.orEmpty().trim()
        val encodedName = encode(safeName)
        return "$SCHEME://$HOST$PATH?$QUERY_UID=$userId&$QUERY_NAME=$encodedName"
    }

    fun parse(payload: String?): UserCard? {
        val safePayload = payload?.trim().orEmpty()
        if (safePayload.isBlank()) return null
        val uri = runCatching { URI(safePayload) }.getOrNull() ?: return null
        if (uri.scheme != SCHEME || uri.host != HOST || uri.path != PATH) return null
        val query = uri.rawQuery.orEmpty()
        val values = query.split("&")
            .filter { it.isNotBlank() }
            .mapNotNull { part ->
                val separator = part.indexOf("=")
                if (separator < 0) return@mapNotNull null
                val key = decode(part.substring(0, separator))
                val value = decode(part.substring(separator + 1))
                key to value
            }
            .toMap()
        val userId = values[QUERY_UID]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        return UserCard(
            userId = userId,
            nickname = values[QUERY_NAME].orEmpty()
        )
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }
}

data class UserCard(
    val userId: Long,
    val nickname: String
)
