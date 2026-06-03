package com.chen.memorizewords.data.account.mapper

import com.chen.memorizewords.data.account.remoteapi.dto.SendSmsCodeResponseDto
import com.chen.memorizewords.domain.account.model.user.SmsCodeMeta

fun SendSmsCodeResponseDto.toDomain(): SmsCodeMeta {
    return SmsCodeMeta(
        expireSeconds = expireSeconds,
        resendIntervalSeconds = resendIntervalSeconds
    )
}

