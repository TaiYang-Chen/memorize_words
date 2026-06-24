package com.chen.memorizewords.domain.sync.service
interface AuthenticatedRequestSuccessReporter {
    suspend fun onAuthenticatedRequestSucceeded()
}
