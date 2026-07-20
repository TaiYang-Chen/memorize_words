package com.chen.memorizewords.feature.floatingreview

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.navigation.fragment.NavHostFragment
import com.chen.memorizewords.core.navigation.CharacterSelectionMode
import com.chen.memorizewords.core.navigation.FloatingWordDestination
import com.chen.memorizewords.core.navigation.FloatingWordEntryExtras
import com.chen.memorizewords.core.navigation.FloatingWordReturnDestination
import com.chen.memorizewords.core.navigation.HomeDestination
import com.chen.memorizewords.core.navigation.HomeEntry
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FloatingReviewActivity : AppCompatActivity() {

    @Inject
    lateinit var homeEntry: HomeEntry

    private lateinit var navHost: NavHostFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.module_floating_review_activity_settings)
        navHost = supportFragmentManager.findFragmentById(
            R.id.module_floating_review_nav_host
        ) as NavHostFragment
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!navHost.navController.popBackStack()) returnToOrigin()
            }
        })
        if (savedInstanceState == null && destination() == FloatingWordDestination.CHARACTER_SELECTION) {
            navHost.navController.navigate(
                R.id.characterPackFragment,
                bundleOf(
                    FloatingWordEntryExtras.EXTRA_CHARACTER_MODE to CharacterSelectionMode.ACTIVATE.name,
                    FloatingWordEntryExtras.EXTRA_ACTIVATION_REQUEST_ID to intent.getStringExtra(
                        FloatingWordEntryExtras.EXTRA_ACTIVATION_REQUEST_ID
                    )
                )
            )
        }
    }

    fun returnToOrigin() {
        if (returnDestination() == FloatingWordReturnDestination.PRACTICE) {
            startActivity(
                homeEntry.createHomeIntent(this, HomeDestination.PRACTICE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
        }
        finish()
    }

    private fun destination(): FloatingWordDestination = runCatching {
        FloatingWordDestination.valueOf(
            intent.getStringExtra(FloatingWordEntryExtras.EXTRA_DESTINATION).orEmpty()
        )
    }.getOrDefault(FloatingWordDestination.SETTINGS)

    private fun returnDestination(): FloatingWordReturnDestination = runCatching {
        FloatingWordReturnDestination.valueOf(
            intent.getStringExtra(FloatingWordEntryExtras.EXTRA_RETURN_DESTINATION).orEmpty()
        )
    }.getOrDefault(FloatingWordReturnDestination.DEFAULT)
}