package com.chen.memorizewords.network.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class ProfileDto(
    val userId: Long,
    val email: String?,
    val nickname: String?,
    val gender: String?,
    val avatarUrl: String?,
    val phone: String?,
    val qq: String?,
    val wechat: String?,
    val emailVerified: Boolean
)
