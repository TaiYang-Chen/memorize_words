package com.chen.memorizewords.network.dto

import com.chen.memorizewords.network.api.datasync.OnboardingStateDto
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
data class SendSmsCodeResponseDto(
    val expireSeconds: Int,
    val resendIntervalSeconds: Int
)
