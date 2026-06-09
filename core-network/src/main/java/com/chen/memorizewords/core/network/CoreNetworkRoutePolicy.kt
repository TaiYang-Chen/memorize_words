package com.chen.memorizewords.core.network

class CoreNetworkRoutePolicy(
    publicPathSuffixes: Set<String> = DEFAULT_PUBLIC_PATH_SUFFIXES,
    clientCacheableGetPathSuffixes: Set<String> = DEFAULT_CLIENT_CACHEABLE_GET_PATH_SUFFIXES
) {
    private val publicPathSuffixes = publicPathSuffixes.map(::normalizePath).toSet()
    private val clientCacheableGetPathSuffixes = clientCacheableGetPathSuffixes.map(::normalizePath).toSet()

    fun isPublicPath(encodedPath: String): Boolean {
        val normalizedPath = normalizePath(encodedPath)
        return publicPathSuffixes.any(normalizedPath::endsWith)
    }

    fun requiresAuthorization(encodedPath: String): Boolean {
        return !isPublicPath(encodedPath)
    }

    fun shouldTreatUnauthorizedAsHttpError(encodedPath: String): Boolean {
        return isPublicPath(encodedPath)
    }

    fun shouldApplyClientCache(method: String, encodedPath: String): Boolean {
        if (method != "GET") return false
        val normalizedPath = normalizePath(encodedPath)
        return clientCacheableGetPathSuffixes.any(normalizedPath::endsWith)
    }

    private fun normalizePath(value: String): String = value.removeSuffix("/")

    private companion object {
        val DEFAULT_PUBLIC_PATH_SUFFIXES = setOf(
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/auth/sms/send-code",
            "/auth/email/send-code"
        )
        val DEFAULT_CLIENT_CACHEABLE_GET_PATH_SUFFIXES = setOf(
            "/practice/providers"
        )
    }
}
