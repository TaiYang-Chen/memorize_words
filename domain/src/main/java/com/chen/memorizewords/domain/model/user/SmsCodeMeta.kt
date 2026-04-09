package com.chen.memorizewords.domain.model.user

data class SmsCodeMeta(
    val expireSeconds: Int,
    val resendIntervalSeconds: Int
)
