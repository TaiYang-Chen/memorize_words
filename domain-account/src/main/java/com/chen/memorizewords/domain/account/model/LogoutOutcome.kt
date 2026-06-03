package com.chen.memorizewords.domain.account.model

sealed class LogoutOutcome {
    data object Success : LogoutOutcome()

    data class LocalClearedRemoteFailed(
        val cause: Throwable
    ) : LogoutOutcome()
}

