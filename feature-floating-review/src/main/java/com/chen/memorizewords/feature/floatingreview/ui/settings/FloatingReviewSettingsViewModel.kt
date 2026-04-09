package com.chen.memorizewords.feature.floatingreview.ui.settings

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.service.floating.FloatingReviewFacade
import com.chen.memorizewords.domain.model.floating.FloatingWordFieldConfig
import com.chen.memorizewords.domain.model.floating.FloatingWordOrderType
import com.chen.memorizewords.domain.model.floating.FloatingWordSettings
import com.chen.memorizewords.domain.model.floating.FloatingWordSourceType
import com.chen.memorizewords.core.navigation.FloatingWordActions
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
    private val floatingReviewFacade: FloatingReviewFacade
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
                navigateRoute(Route.DispatchFloatingAction(FloatingWordActions.ACTION_PREVIEW_CARD))
            }
            if (
                updated.enabled &&
                refreshFloatingContent &&
                shouldRefreshFloatingContent(current, updated)
            ) {
                navigateRoute(Route.DispatchFloatingAction(FloatingWordActions.ACTION_REFRESH))
            }
        }
    }
}
