package com.chen.memorizewords.navigation

import com.chen.memorizewords.domain.account.orchestrator.startup.StartupFloatingAutoStartReader
import com.chen.memorizewords.domain.account.orchestrator.startup.StartupOnboardingStateReader
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.floating.repository.FloatingWordSettingsRepository
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
    private val localAccountRepository: LocalAccountRepository
) : StartupOnboardingStateReader {
    override suspend fun isOnboardingCompleted(): Boolean {
        return localAccountRepository.getCurrentUser()?.onboardingCompleted == true
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
