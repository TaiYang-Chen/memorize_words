package com.chen.memorizewords.navigation

import android.app.Activity
import android.content.Intent
import com.chen.memorizewords.core.navigation.AppLaunchEntry
import com.chen.memorizewords.core.navigation.OnboardingGuardDelegate
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingStep
import com.chen.memorizewords.domain.wordbook.usecase.onboarding.GetCurrentOnboardingStepUseCase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppOnboardingGuardModule {
    @Binds
    @Singleton
    abstract fun bindOnboardingGuardDelegate(
        impl: DefaultOnboardingGuardDelegate
    ): OnboardingGuardDelegate
}

@Singleton
class DefaultOnboardingGuardDelegate @Inject constructor(
    private val getCurrentOnboardingStepUseCase: GetCurrentOnboardingStepUseCase,
    private val appLaunchEntry: AppLaunchEntry
) : OnboardingGuardDelegate {
    override fun guard(activity: Activity): Boolean {
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
