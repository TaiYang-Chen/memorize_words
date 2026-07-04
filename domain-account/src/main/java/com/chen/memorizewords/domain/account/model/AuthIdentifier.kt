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
        AccountNamePolicy.isValid(value) -> AuthIdentifierType.ACCOUNT
        else -> return AuthIdentifier(value = value, type = AuthIdentifierType.ACCOUNT)
    }
    return AuthIdentifier(value = value, type = type)
}

fun classifyValidAuthIdentifier(rawValue: String): AuthIdentifier? {
    val identifier = classifyAuthIdentifier(rawValue)
    return when (identifier.type) {
        AuthIdentifierType.EMAIL,
        AuthIdentifierType.PHONE -> identifier
        AuthIdentifierType.ACCOUNT -> identifier.takeIf { AccountNamePolicy.isValid(it.value) }
    }
}

private val PHONE_PATTERN = "^1[3-9]\\d{9}$".toRegex()
