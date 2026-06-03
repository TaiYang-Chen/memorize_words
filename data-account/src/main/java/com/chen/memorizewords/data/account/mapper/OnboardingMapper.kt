package com.chen.memorizewords.data.account.mapper

import com.chen.memorizewords.data.account.remoteapi.dto.OnboardingStateDto
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingPhase
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot

fun OnboardingStateDto.toDomain(): OnboardingSnapshot {
    return OnboardingSnapshot(
        phase = runCatching { OnboardingPhase.valueOf(phase) }
            .getOrDefault(OnboardingPhase.NEEDS_WORD_BOOK),
        selectedWordBookId = selectedWordBookId,
        revision = revision,
        updatedAt = updatedAt,
        completedAt = completedAt
    )
}

