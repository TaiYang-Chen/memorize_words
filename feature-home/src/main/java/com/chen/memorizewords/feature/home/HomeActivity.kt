package com.chen.memorizewords.feature.home

import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.chen.memorizewords.core.navigation.WordBookEntryDestination
import com.chen.memorizewords.core.ui.activity.BaseVmDbActivity
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.home.databinding.ModuleHomeActivityHomeBinding
import com.chen.memorizewords.feature.home.ui.home.HomeFragment
import com.chen.memorizewords.feature.home.ui.practice.PracticeFragment
import com.chen.memorizewords.feature.home.ui.profile.ProfileFragment
import com.chen.memorizewords.feature.home.ui.stats.StatsFragment
import com.chen.memorizewords.core.navigation.AuthEntry
import com.chen.memorizewords.core.navigation.WordBookEntry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : BaseVmDbActivity<HomeViewModel, ModuleHomeActivityHomeBinding>() {

    companion object {
        private const val EXIT_CONFIRM_WINDOW_MS = 2000L
    }

    override val viewModel: HomeViewModel by lazy {
        ViewModelProvider(this)[HomeViewModel::class.java]
    }

    @Inject
    lateinit var authEntry: AuthEntry

    @Inject
    lateinit var wordBookEntry: WordBookEntry

    private val homeTag = "home_fragment"
    private val practiceTag = "practice_fragment"
    private val statsTag = "stats_fragment"
    private val profileTag = "profile_fragment"
    private var lastBackPressedAtMs: Long = 0L

    override fun createObserver() {
        lifecycleScope.launch {
            viewModel.loginState.collect { logged ->
                logged ?: return@collect
                if (!logged) {
                    startActivity(authEntry.createAuthIntent(this@HomeActivity))
                    finish()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.wordbookState.collect { isSelected ->
                isSelected ?: return@collect
                if (viewModel.loginState.value != true) return@collect
                if (!isSelected) {
                    startActivity(wordBookEntry.createWordBookIntent(this@HomeActivity))
                }
            }
        }
    }

    override fun initView(savedInstanceState: Bundle?) {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExitBackPress()
            }
        })
        setupBottomNav(savedInstanceState == null)
        viewModel.checkAutoLogin()
    }

    private fun setupBottomNav(selectDefault: Boolean) {
        databind.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home -> showHome()
                R.id.menu_practice -> showPractice()
                R.id.menu_stats -> showStats()
                R.id.menu_profile -> showProfile()
            }
            true
        }
        if (selectDefault) {
            databind.bottomNav.selectedItemId = R.id.menu_home
        }
    }

    private fun showHome() {
        showFragment(homeTag) { HomeFragment() }
    }

    private fun showProfile() {
        showFragment(profileTag) { ProfileFragment() }
    }

    private fun showPractice() {
        showFragment(practiceTag) { PracticeFragment() }
    }

    private fun showStats() {
        showFragment(statsTag) { StatsFragment() }
    }

    private fun showFragment(tag: String, factory: () -> androidx.fragment.app.Fragment) {
        val fragmentManager = supportFragmentManager
        val homeFragment = fragmentManager.findFragmentByTag(homeTag)
        val practiceFragment = fragmentManager.findFragmentByTag(practiceTag)
        val statsFragment = fragmentManager.findFragmentByTag(statsTag)
        val profileFragment = fragmentManager.findFragmentByTag(profileTag)
        val target = fragmentManager.findFragmentByTag(tag) ?: factory()

        fragmentManager.beginTransaction().apply {
            if (homeFragment != null) hide(homeFragment)
            if (practiceFragment != null) hide(practiceFragment)
            if (statsFragment != null) hide(statsFragment)
            if (profileFragment != null) hide(profileFragment)
            if (target.isAdded) {
                show(target)
            } else {
                add(R.id.home_fragment_container, target, tag)
            }
        }.commit()
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (event.target) {
            HomeViewModel.Route.ToWordBookUpdates -> {
                startActivity(
                    wordBookEntry.createWordBookIntent(
                        context = this,
                        deepLink = WordBookEntryDestination.myBooksDeepLink(source = "foreground")
                    )
                )
            }
            else -> super.onNavigationRoute(event)
        }
    }

    override fun onConfirmDialog(event: UiEvent.Dialog.Confirm) {
        if (event.action == HomeViewModel.ACTION_WORD_BOOK_UPDATE) {
            viewModel.onWordBookUpdateDialogConfirmed()
            return
        }
        super.onConfirmDialog(event)
    }

    override fun onCancelDialog(event: UiEvent.Dialog.Confirm) {
        if (event.action == HomeViewModel.ACTION_WORD_BOOK_UPDATE) {
            viewModel.onWordBookUpdateDialogIgnored()
            return
        }
        super.onCancelDialog(event)
    }

    private fun handleExitBackPress() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackPressedAtMs <= EXIT_CONFIRM_WINDOW_MS) {
            finish()
            return
        }

        lastBackPressedAtMs = now
        Toast.makeText(this, getString(R.string.home_exit_confirm), Toast.LENGTH_SHORT).show()
    }
}
