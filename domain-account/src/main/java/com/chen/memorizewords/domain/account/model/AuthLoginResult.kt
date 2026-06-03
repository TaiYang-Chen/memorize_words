package com.chen.memorizewords.domain.account.model

import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot

data class AuthLoginResult(
    val user: User,
    val session: AccountSession,
    val onboardingSnapshot: OnboardingSnapshot?
)

