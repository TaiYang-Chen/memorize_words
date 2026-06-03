package com.chen.memorizewords.domain.account.model

data class AccountSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMillis: Long
)

