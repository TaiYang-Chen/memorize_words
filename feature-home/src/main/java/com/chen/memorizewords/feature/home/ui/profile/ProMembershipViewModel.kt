package com.chen.memorizewords.feature.home.ui.profile

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.model.membership.MembershipStatus
import com.chen.memorizewords.domain.account.usecase.membership.CheckInMembershipUseCase
import com.chen.memorizewords.domain.account.usecase.membership.ObserveMembershipStatusUseCase
import com.chen.memorizewords.domain.account.usecase.membership.RefreshMembershipStatusUseCase
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
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    private val checkingIn = MutableStateFlow(false)
    private val membershipStatus = observeMembershipStatusUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<ProMembershipUiState> =
        combine(membershipStatus, checkingIn) { status, isCheckingIn ->
            buildUiState(status, isCheckingIn)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            buildUiState(null, false)
        )

    init {
        viewModelScope.launch {
            refreshMembershipStatusUseCase()
        }
    }

    fun checkIn() {
        if (checkingIn.value || membershipStatus.value?.todayCheckedIn == true) return
        viewModelScope.launch {
            checkingIn.value = true
            try {
                checkInMembershipUseCase()
                    .onSuccess { reward ->
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

    private fun buildUiState(status: MembershipStatus?, checkingIn: Boolean): ProMembershipUiState {
        val active = status?.active == true
        val todayCheckedIn = status?.todayCheckedIn == true
        val title = if (active) {
            resourceProvider.getString(R.string.feature_home_membership_status_active)
        } else {
            resourceProvider.getString(R.string.feature_home_membership_status_normal)
        }
        val validUntilText = formatMembershipValidUntilMinute(status?.validUntilAt)
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
        return ProMembershipUiState(
            title = title,
            subtitle = subtitle,
            note = resourceProvider.getString(R.string.feature_home_membership_note),
            buttonText = buttonText,
            checkInEnabled = !todayCheckedIn && !checkingIn
        )
    }
}

data class ProMembershipUiState(
    val title: String,
    val subtitle: String,
    val note: String,
    val buttonText: String,
    val checkInEnabled: Boolean
)
