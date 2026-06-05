package com.chen.memorizewords.data.account.session

data class AuthSession( // 只表示“登录态”
    val accessToken: String, // 访问 token（短期）
    val refreshToken: String, // 刷新 token（长期）
    val expiresAt: Long // accessToken 过期时间戳（毫秒）
)
