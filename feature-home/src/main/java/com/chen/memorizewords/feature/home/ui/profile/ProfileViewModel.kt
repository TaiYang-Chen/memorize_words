package com.chen.memorizewords.feature.home.ui.profile

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.model.user.User
import com.chen.memorizewords.domain.service.study.StudyStatsFacade
import com.chen.memorizewords.domain.repository.user.LogoutDataLossRiskException
import com.chen.memorizewords.domain.usecase.user.GetUserFlowUseCase
import com.chen.memorizewords.domain.usecase.user.LogoutUseCase
import com.chen.memorizewords.feature.home.R
import com.chen.memorizewords.core.navigation.AuthEntryDestination
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
        const val ACTION_FORCE_LOGOUT = "force_logout"
    }

    sealed interface Route {
        enum class Screen {
            WORD_BOOK,
            FEEDBACK,
            AUTH
        }

        data class Open(
            val screen: Screen,
            val deepLink: String? = null,
            val clearTask: Boolean = false
        ) : Route
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
        navigateRoute(
            Route.Open(
                screen = Route.Screen.WORD_BOOK,
                deepLink = "myapp://favorites"
            )
        )
    }

    fun toFeedback() {
        navigateRoute(
            Route.Open(
                screen = Route.Screen.FEEDBACK,
                deepLink = "myapp://feedback"
            )
        )
    }

    fun toAbout() {
        navigateRoute(
            Route.Open(
                screen = Route.Screen.FEEDBACK,
                deepLink = "myapp://about"
            )
        )
    }

    fun toUserInfo() {
        navigateRoute(
            Route.Open(
                screen = Route.Screen.AUTH,
                deepLink = AuthEntryDestination.USER_PROFILE_DEEP_LINK
            )
        )
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase().onSuccess {
                navigateRoute(
                    Route.Open(
                        screen = Route.Screen.AUTH,
                        clearTask = true
                    )
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
            logoutUseCase(force = true).onFailure { failure ->
                val fallback = resourceProvider.getString(R.string.home_logout_failed)
                showToast(failure.message?.ifBlank { fallback } ?: fallback)
            }
            navigateRoute(
                Route.Open(
                    screen = Route.Screen.AUTH,
                    clearTask = true
                )
            )
        }
    }

    fun onForceLogoutConfirmed() {
        forceLogout()
    }
}
