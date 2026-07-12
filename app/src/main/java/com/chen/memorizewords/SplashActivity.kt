package com.chen.memorizewords

import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.chen.memorizewords.core.navigation.AppRoute
import com.chen.memorizewords.domain.account.orchestrator.startup.StartupOrchestrator
import com.chen.memorizewords.startup.NetworkMonitor
import com.chen.memorizewords.startup.StartupRouteIntentFactory
import com.chen.memorizewords.startup.StartupRouteResolver
import com.chen.memorizewords.startup.appupdate.AppUpdateDialogFragment
import com.chen.memorizewords.startup.appupdate.AppUpdateInstaller
import com.chen.memorizewords.startup.appupdate.AppUpdateStartupCoordinator
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashActivity : AppCompatActivity(), AppUpdateDialogFragment.Listener {

    companion object {
        private const val MIN_SPLASH_DISPLAY_DURATION_MS = 1000L
    }

    @Inject
    lateinit var startupOrchestrator: StartupOrchestrator

    @Inject
    lateinit var startupRouteResolver: StartupRouteResolver

    @Inject
    lateinit var startupRouteIntentFactory: StartupRouteIntentFactory

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var appUpdateStartupCoordinator: AppUpdateStartupCoordinator

    @Inject
    lateinit var appUpdateInstaller: AppUpdateInstaller

    private var hasRouted = false
    private var keepSplashOnScreen = true
    private var pendingTargetRoute: AppRoute? = null
    private var pendingUpdateInfo: com.chen.memorizewords.domain.sync.appupdate.AppUpdateInfo? = null
    private var pendingInstallFile: File? = null
    private var pendingInstallIsForce = false

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val file = pendingInstallFile ?: return@registerForActivityResult
        if (appUpdateInstaller.canInstallPackages()) {
            appUpdateInstaller.install(this, file)
            maybeRouteAfterInstallPermissionReturn()
        } else {
            Toast.makeText(this, "\u8bf7\u5141\u8bb8\u5b89\u88c5\u5e94\u7528\u66f4\u65b0", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(R.color.app_splash_background)

        val splashShownAt = SystemClock.elapsedRealtime()

        lifecycleScope.launch {
            val hasNetwork = networkMonitor.isCurrentlyOnline()
            val targetRoute = startupRouteResolver.resolveRoute(
                startupOrchestrator.resolveLaunchDestinationLocal()
            )
            val updateInfo = appUpdateStartupCoordinator.resolveStartupPrompt(
                hasNetwork = hasNetwork
            )
            val elapsed = SystemClock.elapsedRealtime() - splashShownAt
            val remaining = MIN_SPLASH_DISPLAY_DURATION_MS - elapsed
            if (remaining > 0) {
                delay(remaining)
            }
            if (updateInfo != null) {
                showUpdatePrompt(updateInfo, targetRoute)
            } else {
                routeToTarget(targetRoute)
            }
        }
    }

    override fun onAppUpdateLater(info: AppUpdateDialogFragment.DialogInfo) {
        appUpdateStartupCoordinator.defer(info.releaseId)
        pendingTargetRoute?.let(::routeToTarget)
    }

    override fun onAppUpdateNow(info: AppUpdateDialogFragment.DialogInfo) {
        val updateInfo = pendingUpdateInfo ?: return
        lifecycleScope.launch {
            updateDialog()?.setDownloading(true)
            runCatching {
                val file = appUpdateInstaller.downloadAndVerify(updateInfo) { progress ->
                    updateDialog()?.setDownloadingProgress(progress)
                }
                pendingInstallFile = file
                pendingInstallIsForce = updateInfo.forceUpdate
                if (appUpdateInstaller.canInstallPackages()) {
                    appUpdateInstaller.install(this@SplashActivity, file)
                    maybeRouteAfterInstallStart(updateInfo.forceUpdate)
                } else {
                    installPermissionLauncher.launch(appUpdateInstaller.createInstallPermissionIntent())
                }
            }.onFailure { throwable ->
                updateDialog()?.setDownloading(false)
                Toast.makeText(
                    this@SplashActivity,
                    throwable.message ?: "\u66f4\u65b0\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showUpdatePrompt(
        updateInfo: com.chen.memorizewords.domain.sync.appupdate.AppUpdateInfo,
        targetRoute: AppRoute
    ) {
        pendingUpdateInfo = updateInfo
        pendingTargetRoute = targetRoute
        keepSplashOnScreen = false
        if (supportFragmentManager.findFragmentByTag(AppUpdateDialogFragment.TAG) == null) {
            AppUpdateDialogFragment
                .newInstance(updateInfo)
                .show(supportFragmentManager, AppUpdateDialogFragment.TAG)
        }
    }

    private fun routeToTarget(targetRoute: AppRoute) {
        if (hasRouted) return
        hasRouted = true
        keepSplashOnScreen = false
        startActivity(startupRouteIntentFactory.createIntent(this, targetRoute))
        finish()
    }

    private fun maybeRouteAfterInstallStart(forceUpdate: Boolean) {
        if (forceUpdate) return
        supportFragmentManager.findFragmentByTag(AppUpdateDialogFragment.TAG)
            ?.let { fragment -> (fragment as? AppUpdateDialogFragment)?.dismissAllowingStateLoss() }
        pendingTargetRoute?.let(::routeToTarget)
    }

    private fun maybeRouteAfterInstallPermissionReturn() {
        maybeRouteAfterInstallStart(forceUpdate = pendingInstallIsForce)
    }

    private fun updateDialog(): AppUpdateDialogFragment? {
        return supportFragmentManager.findFragmentByTag(AppUpdateDialogFragment.TAG) as? AppUpdateDialogFragment
    }
}
