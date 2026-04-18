package com.chen.memorizewords.core.navigation

import android.app.Activity
import android.content.Intent
import com.chen.memorizewords.domain.model.onboarding.OnboardingStep
import com.chen.memorizewords.domain.usecase.onboarding.GetCurrentOnboardingStepUseCase
import javax.inject.Inject

class OnboardingGuardDelegate @Inject constructor(
    private val getCurrentOnboardingStepUseCase: GetCurrentOnboardingStepUseCase,
    private val appLaunchEntry: AppLaunchEntry
) {
    fun guard(activity: Activity): Boolean {
        if (getCurrentOnboardingStepUseCase() == OnboardingStep.COMPLETED) {
            return false
        }
        activity.startActivity(
            appLaunchEntry.createLaunchIntent(activity).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        activity.finish()
        return true
    }
}
