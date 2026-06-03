package com.chen.memorizewords.data.account.mapper

import com.chen.memorizewords.data.account.remoteapi.dto.LoginResponseDto
import com.chen.memorizewords.domain.account.model.AccountSession
import com.chen.memorizewords.domain.account.model.AuthLoginResult

fun LoginResponseDto.toDomain(nowEpochMillis: Long): AuthLoginResult {
    val onboardingSnapshot = onboarding?.toDomain()
    return AuthLoginResult(
        user = user.toDomain(onboardingSnapshot),
        session = AccountSession(
            accessToken = token,
            refreshToken = refreshToken,
            expiresAtEpochMillis = nowEpochMillis + expiresIn * 1000L
        ),
        onboardingSnapshot = onboardingSnapshot
    )
}
