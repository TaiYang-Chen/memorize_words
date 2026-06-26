package com.chen.memorizewords.domain.account.model

data class FusionAuthToken(
    val authToken: String,
    val schemeCode: String,
    val expiresIn: Int
)
