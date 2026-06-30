package com.chen.memorizewords.domain.account.model

enum class AuthIdentifierType(val wireValue: String) {
    ACCOUNT("account"),
    EMAIL("email"),
    PHONE("phone")
}

data class AuthIdentifier(
    val value: String,
    val type: AuthIdentifierType
)

fun classifyAuthIdentifier(rawValue: String): AuthIdentifier {
    val value = rawValue.trim()
    val type = when {
        value.contains('@') -> AuthIdentifierType.EMAIL
        PHONE_PATTERN.matches(value) -> AuthIdentifierType.PHONE
        else -> AuthIdentifierType.ACCOUNT
    }
    return AuthIdentifier(value = value, type = type)
}

private val PHONE_PATTERN = "^1[3-9]\\d{9}$".toRegex()
