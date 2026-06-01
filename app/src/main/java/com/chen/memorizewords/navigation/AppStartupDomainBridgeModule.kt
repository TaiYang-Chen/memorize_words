package com.chen.memorizewords.navigation

import com.chen.memorizewords.domain.account.orchestrator.startup.StartupFloatingAutoStartReader
import com.chen.memorizewords.domain.account.orchestrator.startup.StartupOnboardingStateReader
import com.chen.memorizewords.domain.floating.repository.FloatingWordSettingsRepository
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
abstract class AppStartupDomainBridgeModule {
    @Binds
    @Singleton
    abstract fun bindStartupOnboardingStateReader(
        impl: WordBookStartupOnboardingStateReader
    ): StartupOnboardingStateReader

    @Binds
    @Singleton
    abstract fun bindStartupFloatingAutoStartReader(
        impl: FloatingStartupAutoStartReader
    ): StartupFloatingAutoStartReader
}

@Singleton
class WordBookStartupOnboardingStateReader @Inject constructor(
    private val getCurrentOnboardingStepUseCase: GetCurrentOnboardingStepUseCase
) : StartupOnboardingStateReader {
    override fun isOnboardingCompleted(): Boolean {
        return getCurrentOnboardingStepUseCase() == OnboardingStep.COMPLETED
    }
}

@Singleton
class FloatingStartupAutoStartReader @Inject constructor(
    private val floatingWordSettingsRepository: FloatingWordSettingsRepository
) : StartupFloatingAutoStartReader {
    override suspend fun isAutoStartEnabled(): Boolean {
        val settings = floatingWordSettingsRepository.getSettings()
        return settings.enabled && settings.autoStartOnAppLaunch
    }
}
