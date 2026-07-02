package com.chen.memorizewords.feature.feedback.ui.about

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateCheck
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateInfo
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateLocalStateRepository
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateManager
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateStatus
import com.chen.memorizewords.feature.feedback.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val appUpdateManager: AppUpdateManager,
    private val localStateRepository: AppUpdateLocalStateRepository
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
    val updateStatusText: StateFlow<String> =
        appUpdateManager.status
            .map(::formatUpdateStatus)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                _updateStatusText.value
            )

    val appUpdateStatus: StateFlow<AppUpdateStatus> = appUpdateManager.status

    fun onCheckUpdateClicked(versionName: String, versionCode: Int, packageName: String) {
        appUpdateManager.check(
            AppUpdateCheck(
                platform = "ANDROID",
                versionName = versionName,
                versionCode = versionCode,
                channel = APP_UPDATE_CHANNEL,
                packageName = packageName,
                installId = localStateRepository.getOrCreateInstallId()
            ),
            manual = true
        )
    }

    fun onUpdateNowClicked() {
        appUpdateManager.downloadCurrent()
    }

    fun onRemindLaterClicked() {
        appUpdateManager.deferCurrent()
    }

    fun onIgnoreVersionClicked() {
        appUpdateManager.ignoreCurrent()
    }

    fun onInstallStarted(info: AppUpdateInfo, file: File) {
        appUpdateManager.markInstalling(info, file)
    }

    fun onInstallPermissionRequired(info: AppUpdateInfo, file: File) {
        appUpdateManager.markInstallPermissionRequired(info, file)
    }

    fun onInstallFailed(info: AppUpdateInfo?, message: String) {
        appUpdateManager.markInstallFailed(info, message)
    }

    fun resetUpdateStatus() {
        appUpdateManager.reset()
    }

    fun onViewReleaseLogClicked(info: AppUpdateInfo) {
        val url = info.releaseLogUrl?.takeIf { it.isNotBlank() }
            ?: resourceProvider.getString(R.string.feature_feedback_about_release_url)
        navigateRoute(Route.OpenReleasePage(url))
    }

    private fun formatUpdateStatus(status: AppUpdateStatus): String {
        return when (status) {
            AppUpdateStatus.Idle -> resourceProvider.getString(R.string.module_feedback_latest_version)
            AppUpdateStatus.Checking -> resourceProvider.getString(R.string.feature_feedback_update_status_checking)
            AppUpdateStatus.Latest -> resourceProvider.getString(R.string.feature_feedback_update_status_latest)
            is AppUpdateStatus.UpdateAvailable -> resourceProvider.getString(
                R.string.feature_feedback_update_status_available,
                status.info.latestVersion.versionName
            )
            is AppUpdateStatus.Downloading -> resourceProvider.getString(
                R.string.feature_feedback_update_status_downloading,
                status.progress
            )
            is AppUpdateStatus.Downloaded -> resourceProvider.getString(R.string.feature_feedback_update_status_downloaded)
            is AppUpdateStatus.Installing -> resourceProvider.getString(R.string.feature_feedback_update_status_installing)
            is AppUpdateStatus.InstallPermissionRequired -> {
                resourceProvider.getString(R.string.feature_feedback_update_status_permission)
            }
            is AppUpdateStatus.InstallFailed -> resourceProvider.getString(R.string.feature_feedback_update_status_failed)
            is AppUpdateStatus.NetworkError -> resourceProvider.getString(R.string.feature_feedback_update_status_network_error)
            is AppUpdateStatus.VerifyFailed -> resourceProvider.getString(R.string.feature_feedback_update_status_verify_failed)
            is AppUpdateStatus.Ignored -> resourceProvider.getString(R.string.feature_feedback_update_status_ignored)
            is AppUpdateStatus.Deferred -> resourceProvider.getString(R.string.feature_feedback_update_status_deferred)
        }
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

    private companion object {
        const val APP_UPDATE_CHANNEL = "DEFAULT"
    }
}
