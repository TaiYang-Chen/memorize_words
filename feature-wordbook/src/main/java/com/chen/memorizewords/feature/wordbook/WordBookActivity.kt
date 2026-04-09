package com.chen.memorizewords.feature.wordbook

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
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

    override val viewModel: BaseViewModel by lazy {
        ViewModelProvider(this)[BaseViewModel::class.java]
    }

    override fun createObserver() {
    }

    override fun initView(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        handleDeepLink()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        handleDeepLink()
    }

    private fun handleDeepLink() {
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_wordbook) as? NavHostFragment
            ?: return
        navHost.navController.handleDeepLink(intent)
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
