package com.chen.memorizewords.feature.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.navOptions
import androidx.navigation.fragment.NavHostFragment
import com.chen.memorizewords.core.navigation.AppLaunchEntry
import com.chen.memorizewords.core.navigation.HomeEntry
import com.chen.memorizewords.core.navigation.OnboardingEntry
import com.chen.memorizewords.core.ui.activity.BaseVmDbActivity
import com.chen.memorizewords.domain.model.onboarding.OnboardingStep
import com.chen.memorizewords.domain.usecase.user.GetUserFlowUseCase
import com.chen.memorizewords.feature.onboarding.databinding.ActivityOnboardingBinding
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OnboardingActivity : BaseVmDbActivity<OnboardingViewModel, ActivityOnboardingBinding>() {

    override val viewModel: OnboardingViewModel by lazy {
        ViewModelProvider(this)[OnboardingViewModel::class.java]
    }

    @Inject
    lateinit var appLaunchEntry: AppLaunchEntry

    @Inject
    lateinit var homeEntry: HomeEntry

    @Inject
    lateinit var getUserFlowUseCase: GetUserFlowUseCase

    private var hasRoutedAway = false

    override fun createObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.step.collect { step ->
                    when (step) {
                        OnboardingStep.SELECT_WORD_BOOK ->
                            navigateToDestination(R.id.onboardingSelectWordBookFragment)

                        OnboardingStep.SET_STUDY_PLAN ->
                            navigateToDestination(R.id.onboardingStudyPlanFragment)

                        OnboardingStep.COMPLETED -> routeToHome()
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                getUserFlowUseCase().collect { user ->
                    if (user == null) {
                        routeToLaunch()
                    }
                }
            }
        }
    }

    override fun initView(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_onboarding) as? NavHostFragment
            ?: return
        val navController = navHost.navController
        when (viewModel.step.value) {
            OnboardingStep.SELECT_WORD_BOOK -> {
                navController.setGraph(R.navigation.onboarding_nav)
            }

            OnboardingStep.SET_STUDY_PLAN -> {
                val graph = navController.navInflater.inflate(R.navigation.onboarding_nav).apply {
                    setStartDestination(R.id.onboardingStudyPlanFragment)
                }
                navController.setGraph(graph, null)
            }

            OnboardingStep.COMPLETED -> routeToHome()
        }
    }

    override fun navControllerId() = R.id.nav_host_fragment_activity_onboarding

    private fun navigateToDestination(destinationId: Int) {
        if (hasRoutedAway) return
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_onboarding) as? NavHostFragment
            ?: return
        val navController = navHost.navController
        if (navController.currentDestination?.id == destinationId) {
            return
        }
        navController.navigate(
            destinationId,
            null,
            navOptions {
                launchSingleTop = true
                popUpTo(navController.graph.startDestinationId) {
                    inclusive = destinationId == navController.graph.startDestinationId
                }
            }
        )
    }

    private fun routeToHome() {
        if (hasRoutedAway) return
        hasRoutedAway = true
        startActivity(
            homeEntry.createHomeIntent(this).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }

    private fun routeToLaunch() {
        if (hasRoutedAway) return
        hasRoutedAway = true
        startActivity(
            appLaunchEntry.createLaunchIntent(this).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingEntryModule {

    @Binds
    @Singleton
    abstract fun bindOnboardingEntry(impl: DefaultOnboardingEntry): OnboardingEntry
}

@Singleton
class DefaultOnboardingEntry @Inject constructor() : OnboardingEntry {
    override fun createOnboardingIntent(context: Context): Intent {
        return Intent(context, OnboardingActivity::class.java)
    }
}
