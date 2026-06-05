package com.chen.memorizewords.feature.home.ui.profile

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.navigation.AppRoute
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.study.service.StudyStatsFacade
import com.chen.memorizewords.domain.account.model.LogoutOutcome
import com.chen.memorizewords.domain.account.repository.user.LogoutDataLossRiskException
import com.chen.memorizewords.domain.account.usecase.user.GetUserFlowUseCase
import com.chen.memorizewords.domain.account.usecase.user.LogoutUseCase
import com.chen.memorizewords.feature.home.R
import com.chen.memorizewords.core.navigation.AuthEntryDestination
import com.chen.memorizewords.core.navigation.WordBookEntryDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    getUserFlowUseCase: GetUserFlowUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val studyStatsFacade: StudyStatsFacade,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    companion object {
        const val ACTION_LOGOUT_CONFIRM = "logout_confirm"
        const val ACTION_FORCE_LOGOUT = "force_logout"
    }

    sealed interface Route {
        data class OpenAuth(val clearTask: Boolean = false) : Route
    }

    val user: StateFlow<User?> =
        getUserFlowUseCase()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null
            )

    val studyTotalWordCount: StateFlow<Int> =
        studyStatsFacade.getStudyTotalWordCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val studyTotalDayCount: StateFlow<Int> =
        studyStatsFacade.getStudyTotalDayCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val continuousCheckInText: StateFlow<String> =
        studyStatsFacade.getContinuousCheckInDays()
            .map { it.toString() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "--")

    fun toFavorites() {
        navigate(AppRoute.WordBook(deepLink = WordBookEntryDestination.FAVORITES_DEEP_LINK))
    }

    fun toFeedback() {
        navigate(AppRoute.Feedback(deepLink = "myapp://feedback"))
    }

    fun toAbout() {
        navigate(AppRoute.Feedback(deepLink = "myapp://about"))
    }

    fun toUserInfo() {
        navigate(AppRoute.Auth(deepLink = AuthEntryDestination.USER_PROFILE_DEEP_LINK))
    }

    fun requestLogoutConfirmation() {
        showConfirmDialog(
            action = ACTION_LOGOUT_CONFIRM,
            title = resourceProvider.getString(R.string.home_logout_confirm_title),
            message = resourceProvider.getString(R.string.home_logout_confirm_message),
            confirmText = resourceProvider.getString(R.string.home_logout_confirm_action),
            cancelText = resourceProvider.getString(R.string.home_logout_cancel_action)
        )
    }

    private fun logout() {
        viewModelScope.launch {
            logoutUseCase().onSuccess { outcome ->
                if (outcome is LogoutOutcome.LocalClearedRemoteFailed) {
                    val fallback = resourceProvider.getString(R.string.home_logout_failed)
                    showToast(outcome.cause.message?.ifBlank { fallback } ?: fallback)
                }
                navigateRoute(
                    Route.OpenAuth(clearTask = true)
                )
            }.onFailure { failure ->
                if (failure is LogoutDataLossRiskException) {
                    showConfirmDialog(
                        action = ACTION_FORCE_LOGOUT,
                        title = resourceProvider.getString(R.string.home_logout_risk_title),
                        message = resourceProvider.getString(R.string.home_logout_risk_message)
                    )
                    return@onFailure
                }
                val fallback = resourceProvider.getString(R.string.home_logout_failed)
                showToast(failure.message?.ifBlank { fallback } ?: fallback)
            }
        }
    }

    private fun forceLogout() {
        viewModelScope.launch {
            logoutUseCase(force = true).onSuccess { outcome ->
                if (outcome is LogoutOutcome.LocalClearedRemoteFailed) {
                    val fallback = resourceProvider.getString(R.string.home_logout_failed)
                    showToast(outcome.cause.message?.ifBlank { fallback } ?: fallback)
                }
            }.onFailure { failure ->
                val fallback = resourceProvider.getString(R.string.home_logout_failed)
                showToast(failure.message?.ifBlank { fallback } ?: fallback)
            }
            navigateRoute(
                Route.OpenAuth(clearTask = true)
            )
        }
    }

    fun onForceLogoutConfirmed() {
        forceLogout()
    }

    fun onLogoutConfirmed() {
        logout()
    }
}
