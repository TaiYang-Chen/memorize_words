package com.chen.memorizewords.feature.feedback.ui.about

import androidx.annotation.StringRes
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
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    private val _missionText =
        MutableStateFlow(resourceProvider.getString(R.string.module_feedback_mission_text))
    val missionText: StateFlow<String> = _missionText.asStateFlow()

    private val _updateStatusText =
        MutableStateFlow(resourceProvider.getString(R.string.module_feedback_latest_version))
    val updateStatusText: StateFlow<String> = _updateStatusText.asStateFlow()

    fun onCheckUpdateClicked() {
        showToast(_updateStatusText.value)
    }

    fun onRateUsClicked() {
        showPendingMessage(R.string.module_feedback_rate_us)
    }

    fun onOfficialWebsiteClicked() {
        showPendingMessage(R.string.module_feedback_official_website)
    }

    fun onTermsClicked() {
        showPendingMessage(R.string.module_feedback_terms)
    }

    fun onPrivacyClicked() {
        showPendingMessage(R.string.module_feedback_privacy)
    }

    private fun showPendingMessage(@StringRes featureNameRes: Int) {
        showToast(
            resourceProvider.getString(
                R.string.module_feedback_about_feature_pending,
                resourceProvider.getString(featureNameRes)
            )
        )
    }
}
