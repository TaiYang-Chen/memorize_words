package com.chen.memorizewords.navigation

import android.app.Activity
import com.chen.memorizewords.core.navigation.OnboardingGuardDelegate
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
class DefaultOnboardingGuardDelegate @Inject constructor() : OnboardingGuardDelegate {
    override fun guard(activity: Activity): Boolean {
        return false
    }
}
