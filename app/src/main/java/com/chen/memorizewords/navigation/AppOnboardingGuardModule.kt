package com.chen.memorizewords.navigation

import android.app.Activity
import android.content.Intent
import com.chen.memorizewords.core.navigation.OnboardingGuardDelegate
import com.chen.memorizewords.core.navigation.OnboardingEntry
import com.chen.memorizewords.domain.account.auth.AuthStateProvider
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

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
    private val authStateProvider: AuthStateProvider,
    private val localAccountRepository: LocalAccountRepository,
    private val onboardingEntry: OnboardingEntry
) : OnboardingGuardDelegate {
    override fun guard(activity: Activity): Boolean {
        if (!authStateProvider.isAuthenticated()) return false
        val user = runBlocking { localAccountRepository.getCurrentUser() } ?: return false
        if (user.onboardingCompleted) return false

        val intent = onboardingEntry.createOnboardingIntent(activity).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (intent.resolveActivity(activity.packageManager) == null) return false
        activity.startActivity(intent)
        activity.finish()
        return true
    }
}
