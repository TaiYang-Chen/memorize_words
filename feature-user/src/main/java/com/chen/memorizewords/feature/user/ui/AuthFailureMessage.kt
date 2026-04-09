package com.chen.memorizewords.feature.user.ui

internal fun resolveAuthFailureMessage(
    failure: Throwable,
    fallback: String
): String {
    val message = failure.message?.trim().orEmpty()
    return if (message.isBlank() || message.equals("null", ignoreCase = true)) {
        fallback
    } else {
        message
    }
}
