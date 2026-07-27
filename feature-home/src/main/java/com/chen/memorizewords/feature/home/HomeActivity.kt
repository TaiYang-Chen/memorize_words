package com.chen.memorizewords.feature.home

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.chen.memorizewords.core.navigation.AppLaunchEntry
import com.chen.memorizewords.core.navigation.AppRoute
import com.chen.memorizewords.core.navigation.HomeDestination
import com.chen.memorizewords.core.navigation.HomeEntryExtras
import com.chen.memorizewords.core.navigation.RouteNavigator
import com.chen.memorizewords.core.ui.activity.BaseVmDbActivity
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.home.databinding.ModuleHomeActivityHomeBinding
import com.chen.memorizewords.feature.home.ui.home.HomeFragment
import com.chen.memorizewords.feature.home.ui.practice.PracticeFragment
import com.chen.memorizewords.feature.home.ui.profile.ProfileFragment
import com.chen.memorizewords.feature.home.ui.stats.StatsFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : BaseVmDbActivity<HomeViewModel, ModuleHomeActivityHomeBinding>(),
    HomeFragment.HomeTabHost {

    companion object {
        private const val EXIT_CONFIRM_WINDOW_MS = 2000L
        private const val TAB_COLOR_TRANSITION_MS = 160L
    }

    override val viewModel: HomeViewModel by lazy {
        ViewModelProvider(this)[HomeViewModel::class.java]
    }

    @Inject
    lateinit var appLaunchEntry: AppLaunchEntry

    @Inject
    lateinit var routeNavigator: RouteNavigator

    private val homeTag = "home_fragment"
    private val practiceTag = "practice_fragment"
    private val statsTag = "stats_fragment"
    private val profileTag = "profile_fragment"
    private var lastBackPressedAtMs: Long = 0L
    private var statusBarColorAnimator: ValueAnimator? = null

    override fun createObserver() {
        lifecycleScope.launch {
            viewModel.loginState.collect { logged ->
                logged ?: return@collect
                if (!logged) {
                    startActivity(appLaunchEntry.createLaunchIntent(this@HomeActivity))
                    finish()
                }
            }
        }
    }

    override fun initView(savedInstanceState: Bundle?) {
        configureSystemBars()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExitBackPress()
            }
        })
        setupBottomNav(savedInstanceState == null)
        openRequestedDestination()
        viewModel.checkAutoLogin()
    }

    private fun configureSystemBars() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(databind.homeFragmentContainer) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBarInsets.top)
            insets
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequestedDestination()
    }

    private fun setupBottomNav(selectDefault: Boolean) {
        if (selectDefault) {
            showHome(immediate = true)
            databind.bottomNav.menu.findItem(R.id.menu_home).isChecked = true
            updateStatusBarBackground(R.id.menu_home, animate = false)
        }
        databind.bottomNav.setOnItemSelectedListener { item ->
            updateStatusBarBackground(item.itemId)
            when (item.itemId) {
                R.id.menu_home -> showHome()
                R.id.menu_practice -> showPractice()
                R.id.menu_stats -> showStats()
                R.id.menu_profile -> showProfile()
            }
            true
        }
        if (!selectDefault) {
            databind.bottomNav.post {
                updateStatusBarBackground(databind.bottomNav.selectedItemId, animate = false)
            }
        }
    }

    private fun updateStatusBarBackground(menuItemId: Int, animate: Boolean = true) {
        val targetColor = ContextCompat.getColor(this, statusBarBackgroundFor(menuItemId))
        val currentColor = (databind.root.background as? ColorDrawable)?.color ?: targetColor
        statusBarColorAnimator?.cancel()
        if (!animate || currentColor == targetColor) {
            databind.root.setBackgroundColor(targetColor)
            return
        }

        statusBarColorAnimator = ValueAnimator.ofObject(
            ArgbEvaluator(),
            currentColor,
            targetColor
        ).apply {
            duration = TAB_COLOR_TRANSITION_MS
            interpolator = AnimationUtils.loadInterpolator(
                this@HomeActivity,
                android.R.interpolator.fast_out_slow_in
            )
            addUpdateListener { animator ->
                databind.root.setBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    @ColorRes
    private fun statusBarBackgroundFor(menuItemId: Int): Int = when (menuItemId) {
        R.id.menu_practice -> R.color.feature_home_v2_status_bar_practice
        R.id.menu_stats -> R.color.feature_home_v2_status_bar_stats
        R.id.menu_profile -> R.color.feature_home_v2_status_bar_profile
        else -> R.color.feature_home_v2_status_bar_home
    }

    private fun showHome(immediate: Boolean = false) {
        showFragment(homeTag, immediate) { HomeFragment() }
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

    private fun openRequestedDestination() {
        val destination = runCatching {
            HomeDestination.valueOf(
                intent.getStringExtra(HomeEntryExtras.EXTRA_DESTINATION).orEmpty()
            )
        }.getOrDefault(HomeDestination.DEFAULT)
        if (destination == HomeDestination.PRACTICE) {
            databind.bottomNav.selectedItemId = R.id.menu_practice
        }
    }

    override fun openHomeTab(tab: HomeFragment.HomeTab) {
        val itemId = when (tab) {
            HomeFragment.HomeTab.STATS -> R.id.menu_stats
            HomeFragment.HomeTab.PRACTICE -> R.id.menu_practice
            HomeFragment.HomeTab.PROFILE -> R.id.menu_profile
        }
        if (databind.bottomNav.selectedItemId != itemId) {
            databind.bottomNav.selectedItemId = itemId
            return
        }
        when (tab) {
            HomeFragment.HomeTab.STATS -> showStats()
            HomeFragment.HomeTab.PRACTICE -> showPractice()
            HomeFragment.HomeTab.PROFILE -> showProfile()
        }
    }

    private fun showFragment(
        tag: String,
        immediate: Boolean = false,
        factory: () -> androidx.fragment.app.Fragment
    ) {
        val fragmentManager = supportFragmentManager
        val homeFragment = fragmentManager.findFragmentByTag(homeTag)
        val practiceFragment = fragmentManager.findFragmentByTag(practiceTag)
        val statsFragment = fragmentManager.findFragmentByTag(statsTag)
        val profileFragment = fragmentManager.findFragmentByTag(profileTag)
        val target = fragmentManager.findFragmentByTag(tag) ?: factory()
        if (target.isAdded && target.isVisible) return

        fragmentManager.beginTransaction().apply {
            setReorderingAllowed(true)
            if (!immediate) {
                setCustomAnimations(
                    R.anim.feature_home_tab_fade_in,
                    R.anim.feature_home_tab_fade_out
                )
            }
            listOfNotNull(homeFragment, practiceFragment, statsFragment, profileFragment)
                .filter { it !== target && !it.isHidden }
                .forEach(::hide)
            if (target.isAdded) {
                show(target)
            } else {
                add(R.id.home_fragment_container, target, tag)
            }
        }.run {
            if (immediate) {
                commitNow()
            } else {
                commit()
            }
        }
    }

    override fun onDestroy() {
        statusBarColorAnimator?.cancel()
        statusBarColorAnimator = null
        super.onDestroy()
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (event.target) {
            is AppRoute -> routeNavigator.navigate(event.target as AppRoute)
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
