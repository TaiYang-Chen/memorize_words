package com.chen.memorizewords

import android.os.Bundle
import android.os.Debug
import android.os.Environment
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.chen.memorizewords.core.navigation.AuthEntry
import com.chen.memorizewords.core.navigation.HomeEntry
import com.chen.memorizewords.domain.orchestrator.startup.StartupLaunchDestination
import com.chen.memorizewords.domain.orchestrator.startup.StartupOrchestrator
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject


@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var startupOrchestrator: StartupOrchestrator

    @Inject
    lateinit var homeEntry: HomeEntry

    @Inject
    lateinit var authEntry: AuthEntry

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val targetIntent = when (startupOrchestrator.resolveLaunchDestinationFast()) {
            StartupLaunchDestination.HOME -> homeEntry.createHomeIntent(this)
            StartupLaunchDestination.AUTH -> authEntry.createAuthIntent(this)
        }
        startActivity(targetIntent)
        finish()
    }
}
