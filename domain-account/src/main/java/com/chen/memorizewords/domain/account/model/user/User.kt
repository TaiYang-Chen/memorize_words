package com.chen.memorizewords.domain.account.model.user
data class User(
    val userId: Long,
    val email: String?,
    val nickname: String?,
    val gender: String?,
    val avatarUrl: String?,
    val phone: String?,
    val qq: String?,
    val wechat: String?,
    val emailVerified: Boolean,
    val onboardingCompleted: Boolean,
    val localAvatarPath: String? = null
)
