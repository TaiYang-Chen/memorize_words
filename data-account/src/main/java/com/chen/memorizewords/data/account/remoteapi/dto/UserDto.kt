package com.chen.memorizewords.data.account.remoteapi.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class LoginResponseDto(
    val token: String,
    val refreshToken: String,
    val tokenType: String,
    val user: UserDto,
    val expiresIn: Long,
    val refreshTokenExpiresIn: Long,
    val onboarding: OnboardingStateDto? = null
)

@JsonClass(generateAdapter = false)
data class UserDto(
    val id: Long,
    val email: String?,
    val nickname: String?,
    val gender: String?,
    val avatarUrl: String?,
    val phone: String?,
    val qq: String?,
    val wechat: String?,
    val emailVerified: Boolean
)

@JsonClass(generateAdapter = false)
data class OnboardingStateDto(
    val phase: String,
    val selectedWordBookId: Long?,
    val revision: Long,
    val updatedAt: Long,
    val completedAt: Long?
)

@JsonClass(generateAdapter = false)
data class SendSmsCodeResponseDto(
    val expireSeconds: Int,
    val resendIntervalSeconds: Int
)
