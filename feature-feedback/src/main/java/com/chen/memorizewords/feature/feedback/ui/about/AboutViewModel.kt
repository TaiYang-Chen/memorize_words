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
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    sealed interface Route {
        data class OpenUrl(val url: String) : Route
        data class OpenReleasePage(val url: String) : Route
        data class OpenAppMarket(val packageName: String) : Route
    }

    private val _missionText =
        MutableStateFlow(resourceProvider.getString(R.string.module_feedback_mission_text))
    val missionText: StateFlow<String> = _missionText.asStateFlow()

    private val _updateStatusText =
        MutableStateFlow(resourceProvider.getString(R.string.module_feedback_latest_version))
    val updateStatusText: StateFlow<String> = _updateStatusText.asStateFlow()

    fun onCheckUpdateClicked() {
        navigateRoute(
            Route.OpenReleasePage(
                resourceProvider.getString(R.string.feature_feedback_about_release_url)
            )
        )
    }

    fun onRateUsClicked(packageName: String) {
        navigateRoute(Route.OpenAppMarket(packageName))
    }

    fun onOfficialWebsiteClicked() {
        navigateRoute(
            Route.OpenUrl(
                resourceProvider.getString(R.string.feature_feedback_about_official_website_url)
            )
        )
    }

    fun onTermsClicked() {
        navigateRoute(
            Route.OpenUrl(
                resourceProvider.getString(R.string.feature_feedback_about_terms_url)
            )
        )
    }

    fun onPrivacyClicked() {
        navigateRoute(
            Route.OpenUrl(
                resourceProvider.getString(R.string.feature_feedback_about_privacy_url)
            )
        )
    }
}
