package com.chen.memorizewords.domain.model.onboarding

sealed interface OnboardingError {
    data object LocalPersistenceFailed : OnboardingError
    data object RequiredDataUnavailable : OnboardingError
    data object SyncDeferred : OnboardingError
    data class Unknown(val message: String? = null) : OnboardingError
}
