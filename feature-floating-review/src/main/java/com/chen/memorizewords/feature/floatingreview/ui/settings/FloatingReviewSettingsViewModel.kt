package com.chen.memorizewords.feature.floatingreview.ui.settings

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.model.membership.MembershipFeature
import com.chen.memorizewords.domain.account.model.membership.MembershipFeatureAccess
import com.chen.memorizewords.domain.account.usecase.membership.ResolveMembershipFeatureAccessUseCase
import com.chen.memorizewords.domain.floating.service.FloatingReviewFacade
import com.chen.memorizewords.domain.floating.model.FloatingWordFieldConfig
import com.chen.memorizewords.domain.floating.model.FloatingWordOrderType
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceType
import com.chen.memorizewords.core.navigation.FloatingWordActions
import com.chen.memorizewords.feature.floatingreview.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal fun shouldRefreshFloatingContent(
    previous: FloatingWordSettings,
    updated: FloatingWordSettings
): Boolean {
    return previous.sourceType != updated.sourceType ||
        previous.orderType != updated.orderType ||
        previous.selectedWordIds != updated.selectedWordIds ||
        previous.fieldConfigs != updated.fieldConfigs
}

@HiltViewModel
class FloatingReviewSettingsViewModel @Inject constructor(
    private val floatingReviewFacade: FloatingReviewFacade,
    private val resolveMembershipFeatureAccessUseCase: ResolveMembershipFeatureAccessUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    sealed interface Route {
        data class DispatchFloatingAction(val action: String) : Route
    }

    val settings: StateFlow<FloatingWordSettings> =
        floatingReviewFacade.observeSettings()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FloatingWordSettings())

    fun onSourceTypeChanged(sourceType: FloatingWordSourceType) {
        updateSettings { it.copy(sourceType = sourceType) }
    }

    fun onSelectedWordIdsChanged(ids: List<Long>) {
        updateSettings {
            it.copy(
                sourceType = FloatingWordSourceType.SELF_SELECT,
                selectedWordIds = ids
            )
        }
    }

    fun onOrderTypeChanged(orderType: FloatingWordOrderType) {
        updateSettings { it.copy(orderType = orderType) }
    }

    fun onAutoStartChanged(enabled: Boolean) {
        updateSettings(refreshFloatingContent = false) {
            it.copy(autoStartOnAppLaunch = enabled)
        }
    }

    fun onCardOpacityChanged(cardOpacityPercent: Int) {
        updateSettings(
            refreshFloatingContent = false,
            previewCard = true
        ) {
            it.copy(cardOpacityPercent = cardOpacityPercent)
        }
    }

    fun onBallOpacityChanged(ballOpacityPercent: Int) {
        updateSettings(
            refreshFloatingContent = false,
            previewCard = true
        ) {
            it.copy(ballOpacityPercent = ballOpacityPercent)
        }
    }

    fun onBallSizeChanged(ballSizePercent: Int) {
        updateSettings(
            refreshFloatingContent = false,
            previewCard = true
        ) {
            it.copy(ballSizePercent = ballSizePercent)
        }
    }

    fun onCardGapChanged(cardGapDp: Int) {
        updateSettings(
            refreshFloatingContent = false,
            previewCard = true
        ) {
            it.copy(cardGapDp = cardGapDp)
        }
    }

    fun onFieldConfigsChanged(configs: List<FloatingWordFieldConfig>) {
        updateSettings { it.copy(fieldConfigs = configs) }
    }

    private fun updateSettings(
        refreshFloatingContent: Boolean = true,
        previewCard: Boolean = false,
        transform: (FloatingWordSettings) -> FloatingWordSettings
    ) {
        viewModelScope.launch {
            val current = floatingReviewFacade.getSettings()
            val updated = transform(current)
            if (updated == current) return@launch
            floatingReviewFacade.saveSettings(updated)
            if (previewCard) {
                dispatchMemberOnlyFloatingAction(FloatingWordActions.ACTION_PREVIEW_CARD)
            }
            if (
                updated.enabled &&
                refreshFloatingContent &&
                shouldRefreshFloatingContent(current, updated)
            ) {
                dispatchMemberOnlyFloatingAction(FloatingWordActions.ACTION_REFRESH)
            }
        }
    }

    private suspend fun dispatchMemberOnlyFloatingAction(action: String) {
        val access = resolveMembershipFeatureAccessUseCase(MembershipFeature.FLOATING_REVIEW)
        if (access == MembershipFeatureAccess.ALLOWED) {
            navigateRoute(Route.DispatchFloatingAction(action))
        } else {
            showToast(resourceProvider.getString(R.string.module_floating_review_membership_required))
        }
    }
}
