package com.chen.memorizewords.network.policy

object NetworkRoutePolicy {

    private val publicPathSuffixes = setOf(
        "/auth/login",
        "/auth/register",
        "/auth/refresh",
        "/auth/sms/send-code"
    )

    private val clientCacheableGetPathSuffixes = setOf(
        "/practice/providers"
    )

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

    private fun normalizePath(encodedPath: String): String {
        return encodedPath.removeSuffix("/")
    }
}
