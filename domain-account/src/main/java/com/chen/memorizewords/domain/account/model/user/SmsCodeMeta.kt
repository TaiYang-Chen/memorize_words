package com.chen.memorizewords.domain.account.model.user
data class SmsCodeMeta(
    val expireSeconds: Int,
    val resendIntervalSeconds: Int
)
