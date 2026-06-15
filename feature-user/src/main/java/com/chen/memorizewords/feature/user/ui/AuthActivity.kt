package com.chen.memorizewords.feature.user.ui

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.get
import com.chen.memorizewords.core.navigation.AuthEntry
import com.chen.memorizewords.core.navigation.AuthEntryDestination
import com.chen.memorizewords.core.navigation.HomeEntry
import com.chen.memorizewords.core.navigation.OnboardingEntry
import com.chen.memorizewords.core.ui.activity.BaseVmDbActivity
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.auth.AuthStateProvider
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.auth.social.QQAuthProvider
import com.chen.memorizewords.feature.user.databinding.ModuleUserActivityAuthBinding
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class AuthActivity : BaseVmDbActivity<BaseViewModel, ModuleUserActivityAuthBinding>() {

    override val viewModel: BaseViewModel by lazy {
        ViewModelProvider(this)[BaseViewModel::class.java]
    }

    @Inject
    lateinit var authStateProvider: AuthStateProvider

    @Inject
    lateinit var qqAuthProvider: QQAuthProvider

    @Inject
    lateinit var homeEntry: HomeEntry

    @Inject
    lateinit var onboardingEntry: OnboardingEntry

    @Inject
    lateinit var localAccountRepository: LocalAccountRepository

    private var relaunching = false

    override fun createObserver() {
    }

    override fun initView(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            setupAuthGraph()
        }
    }

    private fun setupAuthGraph() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val graph = navController.navInflater.inflate(R.navigation.nav_module_auth)
        graph.setStartDestination(
            if (isUserProfileEntry()) {
                graph[R.id.module_login_profilefragment].id
            } else {
                graph[R.id.loginFragment].id
            }
        )
        navController.graph = graph
    }

    private fun isUserProfileEntry(): Boolean {
        val data = intent?.data ?: return false
        val normalizedUri = buildString {
            append(data.scheme)
            append("://")
            append(data.host)
            append(data.path.orEmpty())
        }
        return normalizedUri == AuthEntryDestination.USER_PROFILE_DEEP_LINK
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        qqAuthProvider.onActivityResultData(requestCode, resultCode, data)
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun finish() {
        if (relaunching) {
            super.finish()
            return
        }

        if (isTaskRoot && authStateProvider.isAuthenticated()) {
            val user = runBlocking { localAccountRepository.getCurrentUser() }
            val launchIntent = if (user?.onboardingCompleted == false) {
                onboardingEntry.createOnboardingIntent(this)
            } else {
                homeEntry.createHomeIntent(this)
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            if (launchIntent.resolveActivity(packageManager) != null) {
                relaunching = true
                startActivity(launchIntent)
            }
        }
        super.finish()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthEntryModule {

    @Binds
    @Singleton
    abstract fun bindAuthEntry(impl: DefaultAuthEntry): AuthEntry
}

@Singleton
class DefaultAuthEntry @Inject constructor() : AuthEntry {
    override fun createAuthIntent(context: android.content.Context): Intent {
        return Intent(context, AuthActivity::class.java)
    }
}
