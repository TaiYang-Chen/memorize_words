package com.chen.memorizewords.feature.user.ui

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.activity.BaseVmDbActivity
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.auth.AuthStateProvider
import com.chen.memorizewords.feature.user.auth.social.QQAuthProvider
import com.chen.memorizewords.feature.user.databinding.ModuleUserActivityAuthBinding
import com.chen.memorizewords.core.navigation.AuthEntry
import com.chen.memorizewords.core.navigation.HomeEntry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

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

    private var relaunching = false

    override fun createObserver() {
    }

    override fun initView(savedInstanceState: Bundle?) {
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
            val homeIntent = homeEntry.createHomeIntent(this).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            val targetIntent = when {
                homeIntent.resolveActivity(packageManager) != null -> homeIntent
                launchIntent != null -> launchIntent
                else -> null
            }

            if (targetIntent != null) {
                relaunching = true
                startActivity(targetIntent)
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
