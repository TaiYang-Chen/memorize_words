package com.chen.memorizewords.core.ui.session.logout

import com.chen.memorizewords.core.session.logout.LogoutHostId
import com.chen.memorizewords.core.session.logout.LogoutState
import com.chen.memorizewords.core.session.logout.LogoutTerminal
import com.chen.memorizewords.core.session.logout.LogoutTerminalLease
import com.chen.memorizewords.core.session.logout.SessionLogoutCoordinator

internal class SessionLogoutHostController(
    private val coordinator: SessionLogoutCoordinator,
    private val callbacks: Callbacks,
    private val loadingMessage: String,
    private val riskTitle: String,
    private val riskMessage: String
) {

    interface Callbacks {
        fun showLoading(message: String, onPresented: (() -> Unit)? = null): Boolean
        fun hideLoading()
        fun showRiskDialog(requestId: Long, title: String, message: String): Boolean
        fun dismissRiskDialog()
        fun onCompleted(terminal: LogoutTerminal)
        fun onFailed(failure: Throwable)
        fun navigateToAuth()
    }

    private val hostId: LogoutHostId = coordinator.createHostId()
    private var loadingRequestId: Long? = null
    private var riskRequestId: Long? = null
    private var terminalLease: LogoutTerminalLease? = null
    private var navigationLease: LogoutTerminalLease.Navigation? = null
    private var blockedNavigationRequestId: Long? = null

    fun render(state: LogoutState) {
        when (state) {
            LogoutState.Idle -> {
                loadingRequestId = null
                riskRequestId = null
                terminalLease = null
                navigationLease = null
                blockedNavigationRequestId = null
                callbacks.hideLoading()
                callbacks.dismissRiskDialog()
            }
            is LogoutState.AwaitingLoadingFrame -> renderAwaitingLoadingFrame(state)
            is LogoutState.Executing -> renderExecuting(state)
            is LogoutState.AwaitingRiskConfirmation -> renderRisk(state)
            is LogoutState.ReadyToNavigate -> renderNavigation(state)
            is LogoutState.Failed -> renderFailure()
            is LogoutState.Navigating -> {
                if (state.hostId == hostId) callbacks.showLoading(loadingMessage)
                else callbacks.hideLoading()
            }
        }
    }

    fun onResume() {
        blockedNavigationRequestId = null
    }

    fun onRiskResult(requestId: Long, confirmed: Boolean) {
        if (riskRequestId != requestId) return
        riskRequestId = null
        if (confirmed) {
            coordinator.confirmDataLossRisk(requestId)
        } else {
            coordinator.cancelDataLossRisk(requestId)
        }
    }

    fun onStop() {
        releaseLoadingHost()
        navigationLease?.let(coordinator::navigationHostStopped)
        navigationLease = null
        terminalLease = null
    }

    fun onDestroy() {
        releaseLoadingHost()
        navigationLease?.let(coordinator::navigationHostStopped)
        navigationLease = null
        terminalLease?.let(coordinator::releaseTerminal)
        terminalLease = null
        riskRequestId?.let { coordinator.releaseRiskDialog(it, hostId) }
        riskRequestId = null
    }

    private fun renderAwaitingLoadingFrame(state: LogoutState.AwaitingLoadingFrame) {
        if (loadingRequestId == state.requestId) return
        if (!coordinator.claimLoadingHost(state.requestId, hostId)) {
            callbacks.hideLoading()
            return
        }
        loadingRequestId = state.requestId
        val shown = callbacks.showLoading(
            message = loadingMessage,
            onPresented = {
                coordinator.onLoadingFramePresented(state.requestId, hostId)
            }
        )
        if (!shown) {
            coordinator.releaseLoadingHost(state.requestId, hostId)
            loadingRequestId = null
        }
    }

    private fun renderExecuting(state: LogoutState.Executing) {
        if (loadingRequestId != state.requestId) {
            if (!coordinator.claimLoadingHost(state.requestId, hostId)) {
                callbacks.hideLoading()
                return
            }
            loadingRequestId = state.requestId
        }
        callbacks.showLoading(loadingMessage)
    }

    private fun renderRisk(state: LogoutState.AwaitingRiskConfirmation) {
        loadingRequestId = null
        callbacks.hideLoading()
        if (riskRequestId == state.requestId) return
        if (!coordinator.claimRiskDialog(state.requestId, hostId)) return
        riskRequestId = state.requestId
        if (!callbacks.showRiskDialog(state.requestId, riskTitle, riskMessage)) {
            coordinator.releaseRiskDialog(state.requestId, hostId)
            riskRequestId = null
        }
    }

    private fun renderNavigation(state: LogoutState.ReadyToNavigate) {
        if (terminalLease != null || navigationLease != null) return
        if (blockedNavigationRequestId == state.requestId) return
        val lease = coordinator.claimTerminal(hostId) as? LogoutTerminalLease.Navigation
        if (lease == null) {
            callbacks.hideLoading()
            return
        }
        terminalLease = lease
        if (!coordinator.beginNavigation(lease)) {
            coordinator.releaseTerminal(lease)
            terminalLease = null
            return
        }
        navigationLease = lease
        terminalLease = null
        try {
            callbacks.navigateToAuth()
        } catch (_: Exception) {
            navigationLease = null
            blockedNavigationRequestId = lease.requestId
            coordinator.navigationFailed(lease)
            callbacks.hideLoading()
            return
        }
        callbacks.onCompleted(lease.terminal)
    }

    private fun renderFailure() {
        loadingRequestId = null
        if (terminalLease != null) return
        val lease = coordinator.claimTerminal(hostId) as? LogoutTerminalLease.Failure ?: return
        terminalLease = lease
        callbacks.hideLoading()
        try {
            callbacks.onFailed(lease.failure)
        } finally {
            coordinator.failureHandled(lease)
            terminalLease = null
        }
    }

    private fun releaseLoadingHost() {
        loadingRequestId?.let { coordinator.releaseLoadingHost(it, hostId) }
        loadingRequestId = null
    }
}
