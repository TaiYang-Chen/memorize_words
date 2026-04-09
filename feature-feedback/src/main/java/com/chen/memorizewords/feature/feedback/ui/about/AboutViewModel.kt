package com.chen.memorizewords.feature.feedback.ui.about

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.feature.feedback.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class AboutViewModel @Inject constructor(
    resourceProvider: ResourceProvider
) : BaseViewModel() {

    private val _missionText =
        MutableStateFlow(resourceProvider.getString(R.string.module_feedback_mission_text))
    val missionText: StateFlow<String> = _missionText.asStateFlow()

    private val _updateStatusText =
        MutableStateFlow(resourceProvider.getString(R.string.module_feedback_update_unavailable))
    val updateStatusText: StateFlow<String> = _updateStatusText.asStateFlow()

    fun onCheckUpdateClicked() {
        showToast(_updateStatusText.value)
    }
}
