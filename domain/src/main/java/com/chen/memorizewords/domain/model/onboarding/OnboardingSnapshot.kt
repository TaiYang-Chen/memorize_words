package com.chen.memorizewords.domain.model.onboarding

data class OnboardingSnapshot(
    val phase: OnboardingPhase = OnboardingPhase.NEEDS_WORD_BOOK,
    val selectedWordBookId: Long? = null,
    val revision: Long = 0L,
    val updatedAt: Long = 0L,
    val completedAt: Long? = null
)
