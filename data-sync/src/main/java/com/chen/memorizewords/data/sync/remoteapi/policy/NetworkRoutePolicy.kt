package com.chen.memorizewords.data.sync.remoteapi.policy

import com.chen.memorizewords.core.network.CoreNetworkRoutePolicy

object NetworkRoutePolicy {
    private val delegate = CoreNetworkRoutePolicy()

    fun isPublicPath(encodedPath: String): Boolean = delegate.isPublicPath(encodedPath)

    fun requiresAuthorization(encodedPath: String): Boolean = delegate.requiresAuthorization(encodedPath)

    fun shouldTreatUnauthorizedAsHttpError(encodedPath: String): Boolean {
        return delegate.shouldTreatUnauthorizedAsHttpError(encodedPath)
    }

    fun shouldApplyClientCache(method: String, encodedPath: String): Boolean {
        return delegate.shouldApplyClientCache(method, encodedPath)
    }
}
