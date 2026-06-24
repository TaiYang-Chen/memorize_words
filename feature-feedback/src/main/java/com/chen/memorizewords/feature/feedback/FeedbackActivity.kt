package com.chen.memorizewords.feature.feedback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import com.chen.memorizewords.core.ui.activity.BaseVmDbActivity
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.feature.feedback.databinding.ModuleFeedbackActivityFeedbackBinding
import com.chen.memorizewords.core.navigation.FeedbackEntry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@AndroidEntryPoint
class FeedbackActivity : BaseVmDbActivity<BaseViewModel, ModuleFeedbackActivityFeedbackBinding>() {

    override val viewModel: BaseViewModel by lazy {
        ViewModelProvider(this)[BaseViewModel::class.java]
    }

    override fun createObserver() {
    }

    override fun initView(savedInstanceState: Bundle?) {
        val navHostFrag = supportFragmentManager.findFragmentById(
            R.id.nav_host_fragment_activity_wordbook
        ) as NavHostFragment
        val navController = navHostFrag.navController
        intent?.let { navController.handleDeepLink(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val navHostFrag = supportFragmentManager.findFragmentById(
            R.id.nav_host_fragment_activity_wordbook
        ) as NavHostFragment
        navHostFrag.navController.handleDeepLink(intent)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FeedbackEntryModule {

    @Binds
    @Singleton
    abstract fun bindFeedbackEntry(impl: DefaultFeedbackEntry): FeedbackEntry
}

@Singleton
class DefaultFeedbackEntry @Inject constructor() : FeedbackEntry {
    override fun createFeedbackIntent(context: Context, deepLink: String?): Intent {
        return Intent(context, FeedbackActivity::class.java).apply {
            deepLink?.let {
                action = Intent.ACTION_VIEW
                data = it.toUri()
            }
        }
    }
}
