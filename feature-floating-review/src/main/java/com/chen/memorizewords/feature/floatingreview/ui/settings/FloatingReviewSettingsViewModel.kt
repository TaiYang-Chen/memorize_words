package com.chen.memorizewords.feature.floatingreview.ui.settings

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.floating.service.FloatingReviewFacade
import com.chen.memorizewords.domain.floating.service.FloatingRuntimeController
import com.chen.memorizewords.domain.floating.model.FloatingWordFieldConfig
import com.chen.memorizewords.domain.floating.model.FloatingWordOrderType
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceType
import com.chen.memorizewords.domain.floating.model.FloatingDevicePreferences
import com.chen.memorizewords.domain.floating.repository.FloatingDevicePreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FloatingReviewSettingsViewModel @Inject constructor(
    private val floatingReviewFacade: FloatingReviewFacade,
    private val devicePreferencesRepository: FloatingDevicePreferencesRepository,
    private val runtimeController: FloatingRuntimeController
) : BaseViewModel() {

    val settings: StateFlow<FloatingWordSettings> =
        floatingReviewFacade.observeSettings()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FloatingWordSettings())

    val devicePreferences = devicePreferencesRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            FloatingDevicePreferences())

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
        viewModelScope.launch {
            devicePreferencesRepository.update { it.copy(autoStartOnAppLaunch = enabled) }
        }
    }

    fun onCardOpacityChanged(cardOpacityPercent: Int) {
        updateSettings { it.copy(cardOpacityPercent = cardOpacityPercent) }
    }

    fun onBallOpacityChanged(ballOpacityPercent: Int) {
        updateSettings { it.copy(ballOpacityPercent = ballOpacityPercent) }
    }

    fun onBallSizeChanged(ballSizePercent: Int) {
        updateSettings { it.copy(ballSizePercent = ballSizePercent) }
    }

    fun onCardGapChanged(cardGapDp: Int) {
        updateSettings { it.copy(cardGapDp = cardGapDp) }
    }

    fun onFieldConfigsChanged(configs: List<FloatingWordFieldConfig>) {
        updateSettings { it.copy(fieldConfigs = configs) }
    }

    private fun updateSettings(transform: (FloatingWordSettings) -> FloatingWordSettings) {
        viewModelScope.launch {
            val current = floatingReviewFacade.getSettings()
            val requested = transform(current)
            if (requested == current) return@launch
            floatingReviewFacade.updateSettings(transform)
            runtimeController.requestReconfigure()
        }
    }
}
