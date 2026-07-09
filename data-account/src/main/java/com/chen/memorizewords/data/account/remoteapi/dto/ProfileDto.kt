package com.chen.memorizewords.data.account.remoteapi.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class ProfileDto(
    val userId: Long,
    val account: String? = null,
    val email: String?,
    val nickname: String?,
    val gender: String?,
    val avatarUrl: String?,
    val phone: String?,
    val qq: String?,
    val wechat: String?,
    val emailVerified: Boolean,
    val onboardingCompleted: Boolean? = null
)
