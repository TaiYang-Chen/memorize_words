package com.chen.memorizewords.data.account.mapper

import com.chen.memorizewords.data.account.remoteapi.dto.ProfileDto
import com.chen.memorizewords.data.account.remoteapi.dto.UserDto
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingPhase
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot

fun UserDto.toDomain(onboardingSnapshot: OnboardingSnapshot? = null): User {
    return User(
        userId = id,
        email = email,
        nickname = nickname,
        gender = gender,
        avatarUrl = avatarUrl,
        phone = phone,
        qq = qq,
        wechat = wechat,
        emailVerified = emailVerified,
        onboardingCompleted = onboardingSnapshot.isCompleted()
    )
}

fun ProfileDto.toDomain(onboardingCompleted: Boolean = false): User {
    return User(
        userId = userId,
        email = email,
        nickname = nickname,
        gender = gender,
        avatarUrl = avatarUrl,
        phone = phone,
        qq = qq,
        wechat = wechat,
        emailVerified = emailVerified,
        onboardingCompleted = onboardingCompleted
    )
}

private fun OnboardingSnapshot?.isCompleted(): Boolean {
    return this?.phase == OnboardingPhase.COMPLETED || this?.completedAt != null
}
