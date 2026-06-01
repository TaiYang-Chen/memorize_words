package com.chen.memorizewords.feature.wordbook

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment
import com.chen.memorizewords.core.navigation.OnboardingGuardDelegate
import com.chen.memorizewords.core.ui.activity.BaseVmDbActivity
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.feature.wordbook.databinding.ActivityWordBookBinding
import com.chen.memorizewords.core.navigation.WordBookEntry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@AndroidEntryPoint
class WordBookActivity : BaseVmDbActivity<BaseViewModel, ActivityWordBookBinding>() {

    @Inject
    lateinit var onboardingGuardDelegate: OnboardingGuardDelegate

    override val viewModel: BaseViewModel by lazy {
        ViewModelProvider(this)[BaseViewModel::class.java]
    }

    override fun createObserver() {
    }

    override fun initView(savedInstanceState: Bundle?) {
        if (onboardingGuardDelegate.guard(this)) return
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            initializeNavigation(intent)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        if (onboardingGuardDelegate.guard(this)) return
        initializeNavigation(intent)
    }

    private fun initializeNavigation(intent: Intent?) {
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_wordbook) as? NavHostFragment
            ?: return
        val navController = navHost.navController
        val launchTarget = WordBookDeepLinkResolver.resolve(intent?.dataString)
        navController.setGraph(
            buildNavigationGraph(navController, launchTarget),
            launchTarget.toStartArgs()
        )
    }

    private fun buildNavigationGraph(
        navController: NavController,
        launchTarget: WordBookLaunchTarget?
    ): NavGraph {
        return navController.navInflater.inflate(R.navigation.wordbook_nav).apply {
            launchTarget?.let {
                setStartDestination(it.destination.toNavDestinationId())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        onboardingGuardDelegate.guard(this)
    }
}

private fun WordBookLaunchTarget?.toStartArgs(): Bundle? {
    val target = this ?: return null
    return when (target.destination) {
        WordBookLaunchTarget.Destination.FAVORITES,
        WordBookLaunchTarget.Destination.SHOP -> null

        WordBookLaunchTarget.Destination.MY_WORD_BOOKS -> bundleOf(
            "source" to target.source
        )
    }
}

private fun WordBookLaunchTarget.Destination.toNavDestinationId(): Int {
    return when (this) {
        WordBookLaunchTarget.Destination.FAVORITES -> R.id.favoritesFragment
        WordBookLaunchTarget.Destination.MY_WORD_BOOKS -> R.id.myWordBooksFragment
        WordBookLaunchTarget.Destination.SHOP -> R.id.shopFragment
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WordBookEntryModule {

    @Binds
    @Singleton
    abstract fun bindWordBookEntry(impl: DefaultWordBookEntry): WordBookEntry
}

@Singleton
class DefaultWordBookEntry @Inject constructor() : WordBookEntry {
    override fun createWordBookIntent(context: Context, deepLink: String?): Intent {
        return Intent(context, WordBookActivity::class.java).apply {
            deepLink?.let {
                action = Intent.ACTION_VIEW
                data = Uri.parse(it)
            }
        }
    }
}
