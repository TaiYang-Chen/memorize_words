package com.chen.memorizewords.feature.user.auth.social

data class SocialAuthCredential(
    val oauthCode: String,
    val state: String? = null
)
