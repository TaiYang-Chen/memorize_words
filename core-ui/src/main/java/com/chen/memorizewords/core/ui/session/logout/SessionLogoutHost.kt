package com.chen.memorizewords.core.ui.session.logout

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.core.session.logout.LogoutTerminal
import com.chen.memorizewords.core.session.logout.SessionLogoutCoordinator
import com.chen.memorizewords.core.ui.dialog.loading.LoadingDialogController
import com.chen.memorizewords.core.ui.dialog.prefabricated.ResultConfirmDialogFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SessionLogoutHost(
    private val fragment: Fragment,
    private val coordinator: SessionLogoutCoordinator,
    private val configuration: Configuration
) {

    data class Configuration(
        val loadingMessage: String,
        val riskTitle: String,
        val riskMessage: String,
        val onCompleted: (LogoutTerminal) -> Unit,
        val onFailed: (Throwable) -> Unit,
        val navigateToAuth: () -> Unit
    )

    private val loadingController by lazy {
        LoadingDialogController(fragment.parentFragmentManager, LOADING_TAG)
    }
    private val controller = SessionLogoutHostController(
        coordinator = coordinator,
        callbacks = HostCallbacks(),
        loadingMessage = configuration.loadingMessage,
        riskTitle = configuration.riskTitle,
        riskMessage = configuration.riskMessage
    )
    private var observation: Job? = null

    fun bind() {
        if (observation != null) return
        fragment.parentFragmentManager.setFragmentResultListener(
            RISK_RESULT_KEY,
            fragment.viewLifecycleOwner
        ) { _, result ->
            controller.onRiskResult(
                requestId = result.getLong(ResultConfirmDialogFragment.RESULT_REQUEST_ID),
                confirmed = result.getBoolean(ResultConfirmDialogFragment.RESULT_CONFIRMED)
            )
        }
        fragment.viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = controller.onResume()
            override fun onStop(owner: LifecycleOwner) = controller.onStop()
            override fun onDestroy(owner: LifecycleOwner) = controller.onDestroy()
        })
        observation = fragment.viewLifecycleOwner.lifecycleScope.launch {
            fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                coordinator.state.collect(controller::render)
            }
        }
    }

    private inner class HostCallbacks : SessionLogoutHostController.Callbacks {
        override fun showLoading(message: String, onPresented: (() -> Unit)?): Boolean {
            return loadingController.show(message, onPresented)
        }

        override fun hideLoading() = loadingController.hide()

        override fun showRiskDialog(requestId: Long, title: String, message: String): Boolean {
            val manager = fragment.parentFragmentManager
            if (manager.isStateSaved) return false
            if (manager.findFragmentByTag(RISK_TAG) != null) return true
            ResultConfirmDialogFragment.newInstance(
                resultKey = RISK_RESULT_KEY,
                requestId = requestId,
                title = title,
                message = message,
                cancelable = false
            ).show(manager, RISK_TAG)
            return true
        }

        override fun dismissRiskDialog() {
            (fragment.parentFragmentManager.findFragmentByTag(RISK_TAG) as? DialogFragment)
                ?.dismissAllowingStateLoss()
        }

        override fun onCompleted(terminal: LogoutTerminal) {
            configuration.onCompleted(terminal)
        }

        override fun onFailed(failure: Throwable) = configuration.onFailed(failure)

        override fun navigateToAuth() = configuration.navigateToAuth()
    }

    private companion object {
        const val LOADING_TAG = "SessionLogoutLoadingDialog"
        const val RISK_TAG = "SessionLogoutRiskDialog"
        const val RISK_RESULT_KEY = "SessionLogoutRiskDialogResult"
    }
}
