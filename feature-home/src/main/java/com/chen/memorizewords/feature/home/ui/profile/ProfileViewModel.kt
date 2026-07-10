package com.chen.memorizewords.feature.home.ui.profile

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.navigation.AppRoute
import com.chen.memorizewords.core.navigation.AuthEntryDestination
import com.chen.memorizewords.core.navigation.WordBookEntryDestination
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.model.LogoutOutcome
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.LogoutDataLossRiskException
import com.chen.memorizewords.domain.account.usecase.user.CacheLoadedAvatarUseCase
import com.chen.memorizewords.domain.account.usecase.user.GetUserFlowUseCase
import com.chen.memorizewords.domain.account.usecase.user.LogoutUseCase
import com.chen.memorizewords.domain.account.usecase.membership.ObserveMembershipStatusUseCase
import com.chen.memorizewords.domain.account.usecase.membership.RefreshMembershipStatusUseCase
import com.chen.memorizewords.domain.study.model.record.DailyDurationStats
import com.chen.memorizewords.domain.study.model.record.DailyWordStats
import com.chen.memorizewords.domain.study.service.StudyStatsFacade
import com.chen.memorizewords.feature.home.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    getUserFlowUseCase: GetUserFlowUseCase,
    private val logoutUseCase: LogoutUseCase,
    observeMembershipStatusUseCase: ObserveMembershipStatusUseCase,
    private val refreshMembershipStatusUseCase: RefreshMembershipStatusUseCase,
    private val studyStatsFacade: StudyStatsFacade,
    private val cacheLoadedAvatarUseCase: CacheLoadedAvatarUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    companion object {
        const val ACTION_LOGOUT_CONFIRM = "logout_confirm"
        const val ACTION_FORCE_LOGOUT = "force_logout"
        private const val CURRENT_BUSINESS_DATE_REFRESH_INTERVAL_MS = 60_000L
    }

    sealed interface Route {
        data class OpenAuth(val clearTask: Boolean = false) : Route
        data object OpenMembership : Route
        data object OpenPersonalQr : Route
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
    val studyTotalWordCountText: StateFlow<String> =
        studyTotalWordCount
            .map { it.toString() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "0")
    val studyTotalDayCount: StateFlow<Int> =
        studyStatsFacade.getStudyTotalDayCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val continuousCheckInDays = studyStatsFacade.getContinuousCheckInDays()

    private val membershipStatus =
        observeMembershipStatusUseCase()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val currentBusinessDate: StateFlow<String> =
        flow {
            while (true) {
                emit(studyStatsFacade.getCurrentBusinessDate())
                delay(CURRENT_BUSINESS_DATE_REFRESH_INTERVAL_MS)
            }
        }
            .distinctUntilChanged()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                studyStatsFacade.getCurrentBusinessDate()
            )

    val continuousCheckInText: StateFlow<String> =
        continuousCheckInDays
            .map { it.toString() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "--")

    val longestCheckInText: StateFlow<String> =
        continuousCheckInDays
            .map { days ->
                resourceProvider.getString(R.string.feature_home_profile_longest_days, days)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                resourceProvider.getString(R.string.feature_home_profile_longest_days, 0)
            )

    val profileHeroStreakText: StateFlow<String> =
        continuousCheckInDays
            .map {
                resourceProvider.getString(R.string.feature_home_profile_hero_streak, it)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                resourceProvider.getString(R.string.feature_home_profile_hero_streak, 0)
            )

    val profileNicknameText: StateFlow<String> =
        user.map { currentUser ->
            resolveProfileNickname(
                currentUser,
                resourceProvider.getString(R.string.feature_home_profile_empty_nickname)
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            resourceProvider.getString(R.string.feature_home_profile_empty_nickname)
        )

    val profileAccountIdText: StateFlow<String> =
        user.map { currentUser ->
            resolveProfileAccountId(
                currentUser,
                resourceProvider.getString(R.string.feature_home_profile_empty_account_id)
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            resourceProvider.getString(R.string.feature_home_profile_empty_account_id)
        )

    val profileAccountIdDisplayText: StateFlow<String> =
        profileAccountIdText.map { accountId ->
            resourceProvider.getString(R.string.feature_home_profile_account_id, accountId)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            resourceProvider.getString(
                R.string.feature_home_profile_account_id,
                resourceProvider.getString(R.string.feature_home_profile_empty_account_id)
            )
        )

    val membershipTitleText: StateFlow<String> =
        membershipStatus
            .map { status ->
                if (status?.active == true) {
                    resourceProvider.getString(R.string.feature_home_profile_member_active_title)
                } else {
                    resourceProvider.getString(R.string.feature_home_profile_member_checkin_title)
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                resourceProvider.getString(R.string.feature_home_profile_member_checkin_title)
            )

    val membershipSubtitleText: StateFlow<String> =
        membershipStatus
            .map { status ->
                val validUntilText = formatMembershipValidUntilMinute(status?.validUntilAtMs)
                if (status?.active == true && !validUntilText.isNullOrBlank()) {
                    resourceProvider.getString(
                        R.string.feature_home_profile_member_valid_until,
                        validUntilText
                    )
                } else {
                    resourceProvider.getString(R.string.feature_home_profile_member_checkin_subtitle)
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                resourceProvider.getString(R.string.feature_home_profile_member_checkin_subtitle)
            )

    val membershipActionText: StateFlow<String> =
        membershipStatus
            .map { status ->
                if (status?.todayCheckedIn == true) {
                    resourceProvider.getString(R.string.feature_home_profile_member_checked_today)
                } else {
                    resourceProvider.getString(R.string.feature_home_profile_member_checkin_action)
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                resourceProvider.getString(R.string.feature_home_profile_member_checkin_action)
            )

    val totalStudyDurationHoursText: StateFlow<String> =
        studyStatsFacade.getStudyTotalDurationMs()
            .map(::formatTotalDurationHours)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "0")

    private val yesterdayWordStats: StateFlow<List<DailyWordStats>> =
        currentBusinessDate
            .map(::previousBusinessDate)
            .flatMapLatest { yesterday ->
                studyStatsFacade.getDailyWordStats(yesterday, yesterday)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val wordsVsYesterdayText: StateFlow<String> =
        combine(
            studyStatsFacade.getTodayNewWordCount(),
            studyStatsFacade.getTodayReviewWordCount(),
            yesterdayWordStats
        ) { todayNewCount, todayReviewCount, yesterdayStats ->
            val todayCount = todayNewCount + todayReviewCount
            val yesterdayCount = yesterdayStats.sumOf { it.newCount + it.reviewCount }
            resourceProvider.getString(
                R.string.feature_home_profile_vs_yesterday_count,
                formatSignedInt(todayCount - yesterdayCount)
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            resourceProvider.getString(R.string.feature_home_profile_vs_yesterday_count, "+0")
        )

    private val yesterdayDurationStats: StateFlow<List<DailyDurationStats>> =
        currentBusinessDate
            .map(::previousBusinessDate)
            .flatMapLatest { yesterday ->
                studyStatsFacade.getDailyDurationStats(yesterday, yesterday)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val durationVsYesterdayText: StateFlow<String> =
        combine(
            studyStatsFacade.getTodayStudyDurationMs(),
            yesterdayDurationStats
        ) { todayDurationMs, yesterdayStats ->
            val yesterdayDurationMs = yesterdayStats.sumOf { it.durationMs }
            resourceProvider.getString(
                R.string.feature_home_profile_vs_yesterday_hours_dynamic,
                formatSignedHours(todayDurationMs - yesterdayDurationMs)
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            resourceProvider.getString(R.string.feature_home_profile_vs_yesterday_hours_dynamic, "+0h")
        )

    fun toFavorites() {
        navigate(AppRoute.WordBook(deepLink = WordBookEntryDestination.FAVORITES_DEEP_LINK))
    }

    fun toMyWordBooks() {
        navigate(AppRoute.WordBook(deepLink = WordBookEntryDestination.MY_BOOKS_DEEP_LINK))
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

    fun toLearningReport() {
        showToast(resourceProvider.getString(R.string.feature_home_profile_learning_report_hint))
    }

    fun toSettings() {
        toUserInfo()
    }

    fun toMembership() {
        navigateRoute(Route.OpenMembership)
    }

    fun toPersonalQr() {
        navigateRoute(Route.OpenPersonalQr)
    }

    init {
        viewModelScope.launch {
            refreshMembershipStatusUseCase()
        }
    }

    fun cacheLoadedAvatar(imageBytes: ByteArray, avatarUrl: String?) {
        viewModelScope.launch {
            cacheLoadedAvatarUseCase(imageBytes, avatarUrl)
        }
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
        performLogout(force = false)
    }

    private fun forceLogout() {
        performLogout(force = true)
    }

    private fun performLogout(force: Boolean) {
        viewModelScope.launch {
            val result = withLoading(resourceProvider.getString(R.string.home_logout_loading)) {
                logoutUseCase(force = force)
            }
            if (force) {
                result.onSuccess { outcome ->
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
                return@launch
            }

            result.onSuccess { outcome ->
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

    fun onForceLogoutConfirmed() {
        forceLogout()
    }

    fun onLogoutConfirmed() {
        logout()
    }
}

internal fun resolveProfileNickname(user: User?, fallback: String): String {
    return user?.nickname?.trim()?.takeIf { it.isNotBlank() } ?: fallback
}

internal fun resolveProfileAccountId(user: User?, fallback: String): String {
    return user?.userId?.takeIf { it > 0L }?.toString() ?: fallback
}

internal fun formatTotalDurationHours(durationMs: Long): String {
    return (durationMs.coerceAtLeast(0L) / 3_600_000L).toString()
}

internal fun formatSignedInt(value: Int): String {
    return if (value > 0) "+$value" else value.toString()
}

internal fun formatSignedHours(durationMs: Long): String {
    val hours = durationMs / 3_600_000.0
    val formatted = DecimalFormat("0.#", DecimalFormatSymbols(Locale.US)).format(kotlin.math.abs(hours))
    return when {
        durationMs > 0L -> "+${formatted}h"
        durationMs < 0L -> "-${formatted}h"
        else -> "0h"
    }
}

internal fun previousBusinessDate(date: String): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return synchronized(dateFormat) {
        val parsedDate = runCatching { dateFormat.parse(date) }.getOrNull() ?: return date
        val calendar = Calendar.getInstance().apply {
            time = parsedDate
            add(Calendar.DAY_OF_MONTH, -1)
        }
        dateFormat.format(calendar.time)
    }
}

internal fun formatMembershipValidUntilMinute(validUntilAtMs: Long?): String? {
    validUntilAtMs ?: return null
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(validUntilAtMs))
}
