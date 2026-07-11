package com.chen.memorizewords.feature.home.ui.profile

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.model.membership.MembershipStatus
import com.chen.memorizewords.domain.account.usecase.membership.CheckInMembershipUseCase
import com.chen.memorizewords.domain.account.usecase.membership.ObserveMembershipStatusUseCase
import com.chen.memorizewords.domain.account.usecase.membership.RefreshMembershipStatusUseCase
import com.chen.memorizewords.domain.practice.usage.ObservePracticeUsageUseCase
import com.chen.memorizewords.domain.practice.usage.PracticeUsageState
import com.chen.memorizewords.domain.practice.usage.RefreshPracticeUsageUseCase
import com.chen.memorizewords.feature.home.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProMembershipViewModel @Inject constructor(
    observeMembershipStatusUseCase: ObserveMembershipStatusUseCase,
    private val refreshMembershipStatusUseCase: RefreshMembershipStatusUseCase,
    private val checkInMembershipUseCase: CheckInMembershipUseCase,
    observePracticeUsageUseCase: ObservePracticeUsageUseCase,
    private val refreshPracticeUsageUseCase: RefreshPracticeUsageUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    private val checkingIn = MutableStateFlow(false)
    private val membershipStatus = observeMembershipStatusUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val practiceUsage = observePracticeUsageUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PracticeUsageState.Unknown)

    val uiState: StateFlow<ProMembershipUiState> =
        combine(membershipStatus, checkingIn, practiceUsage) { status, isCheckingIn, usage ->
            buildUiState(status, isCheckingIn, usage)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            buildUiState(null, false, PracticeUsageState.Unknown)
        )

    init {
        viewModelScope.launch {
            refreshMembershipStatusUseCase()
            refreshPracticeUsageUseCase()
        }
    }

    fun checkIn() {
        if (checkingIn.value || membershipStatus.value?.todayCheckedIn == true) return
        viewModelScope.launch {
            checkingIn.value = true
            try {
                checkInMembershipUseCase()
                    .onSuccess { reward ->
                        refreshPracticeUsageUseCase()
                        showToast(
                            resourceProvider.getString(
                                if (reward.granted) {
                                    R.string.feature_home_membership_checkin_success
                                } else {
                                    R.string.feature_home_membership_checkin_duplicate
                                }
                            )
                        )
                    }
                    .onFailure {
                        showToast(resourceProvider.getString(R.string.feature_home_membership_checkin_failed))
                    }
            } finally {
                checkingIn.value = false
            }
        }
    }

    private fun buildUiState(
        status: MembershipStatus?,
        checkingIn: Boolean,
        usageState: PracticeUsageState
    ): ProMembershipUiState {
        val active = status?.active == true
        val todayCheckedIn = status?.todayCheckedIn == true
        val title = if (active) {
            resourceProvider.getString(R.string.feature_home_membership_status_active)
        } else {
            resourceProvider.getString(R.string.feature_home_membership_status_normal)
        }
        val validUntilText = formatMembershipValidUntilMinute(status?.validUntilAtMs)
        val subtitle = if (active && !validUntilText.isNullOrBlank()) {
            resourceProvider.getString(
                R.string.feature_home_membership_status_active_subtitle,
                validUntilText,
                status?.remainingDays ?: 0
            )
        } else {
            resourceProvider.getString(R.string.feature_home_membership_status_normal_subtitle)
        }
        val buttonText = if (todayCheckedIn) {
            resourceProvider.getString(R.string.feature_home_membership_checked_button)
        } else {
            resourceProvider.getString(R.string.feature_home_membership_checkin_button)
        }
        val evaluation = when (usageState) {
            is PracticeUsageState.Available -> usageState.usage.evaluation
            is PracticeUsageState.Stale -> usageState.usage.evaluation
            is PracticeUsageState.Exhausted -> usageState.usage.evaluation
            else -> null
        }
        return ProMembershipUiState(
            title = title,
            subtitle = subtitle,
            note = resourceProvider.getString(R.string.feature_home_membership_note),
            buttonText = buttonText,
            checkInEnabled = !todayCheckedIn && !checkingIn,
            evaluationBenefitText = evaluation?.let {
                resourceProvider.getString(
                    R.string.feature_home_membership_evaluation_policy,
                    it.policy.freeDailyLimit,
                    it.policy.memberDailyLimit
                )
            } ?: resourceProvider.getString(R.string.feature_home_membership_evaluation_unknown),
            evaluationUsageText = evaluation?.let {
                resourceProvider.getString(
                    R.string.feature_home_membership_evaluation_usage,
                    it.used,
                    it.dailyLimit,
                    it.remaining
                )
            } ?: resourceProvider.getString(R.string.feature_home_membership_evaluation_usage_unknown)
        )
    }
}

data class ProMembershipUiState(
    val title: String,
    val subtitle: String,
    val note: String,
    val buttonText: String,
    val checkInEnabled: Boolean,
    val evaluationBenefitText: String,
    val evaluationUsageText: String
)
