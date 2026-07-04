package com.chen.memorizewords.domain.account.model

object AccountNamePolicy {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 20

    fun isValid(rawValue: String): Boolean {
        val value = rawValue.trim()
        return value.length in MIN_LENGTH..MAX_LENGTH &&
            ACCOUNT_PATTERN.matches(value) &&
            value.any { it in 'A'..'Z' || it in 'a'..'z' }
    }
}

private val ACCOUNT_PATTERN = "^[A-Za-z0-9]+$".toRegex()
