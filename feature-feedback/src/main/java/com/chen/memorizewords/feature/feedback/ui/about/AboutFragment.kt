package com.chen.memorizewords.feature.feedback.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.core.ui.R as CoreUiR
import com.chen.memorizewords.core.ui.ext.dimenPx
import com.chen.memorizewords.core.ui.ext.dimenPxFloat
import com.chen.memorizewords.core.ui.ext.setTextSizeFromDimen
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateInfo
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateStatus
import com.chen.memorizewords.feature.feedback.R
import com.chen.memorizewords.feature.feedback.databinding.ModuleFeedbackFragmentAboutBinding
import com.chen.memorizewords.feature.feedback.ui.util.AppInfoProvider
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AboutFragment : BaseFragment<AboutViewModel, ModuleFeedbackFragmentAboutBinding>() {

    override val viewModel: AboutViewModel by lazy {
        ViewModelProvider(this)[AboutViewModel::class.java]
    }

    private var updateDialog: AlertDialog? = null
    private var pendingInstall: Pair<AppUpdateInfo, File>? = null
    private var downloadingReleaseId: Long? = null
    private var downloadingProgressBar: ProgressBar? = null
    private var downloadingProgressText: TextView? = null

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val (info, file) = pendingInstall ?: return@registerForActivityResult
        if (canInstallPackages()) {
            installUpdate(info, file)
        } else {
            viewModel.onInstallFailed(info, getString(R.string.feature_feedback_update_error_install_permission))
        }
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.tvVersion.text = getString(
            R.string.module_feedback_version_label,
            AppInfoProvider.getVersionName(requireContext())
        )
        databind.rowCheckUpdate.setOnClickListener {
            viewModel.onCheckUpdateClicked(
                versionName = AppInfoProvider.getVersionName(requireContext()),
                versionCode = AppInfoProvider.getVersionCode(requireContext()).toInt(),
                packageName = requireContext().packageName
            )
        }
        databind.rowRateUs.setOnClickListener {
            viewModel.onRateUsClicked(requireContext().packageName)
        }
        databind.rowOfficialWebsite.setOnClickListener {
            viewModel.onOfficialWebsiteClicked()
        }
        databind.rowTerms.setOnClickListener {
            viewModel.onTermsClicked()
        }
        databind.rowPrivacy.setOnClickListener {
            viewModel.onPrivacyClicked()
        }
    }

    override fun createObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.missionText.collect { text ->
                        databind.tvMission.text = text
                    }
                }
                launch {
                    viewModel.updateStatusText.collect { text ->
                        databind.tvCheckUpdateStatus.text = text
                    }
                }
                launch {
                    viewModel.appUpdateStatus.collect(::renderUpdateStatus)
                }
            }
        }
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (val target = event.target) {
            is AboutViewModel.Route.OpenReleasePage -> openUrl(target.url)
            is AboutViewModel.Route.OpenUrl -> openUrl(target.url)
            is AboutViewModel.Route.OpenAppMarket -> openAppMarket(target.packageName)
            else -> super.onNavigationRoute(event)
        }
    }

    private fun openAppMarket(packageName: String) {
        val safePackage = packageName.trim()
        if (safePackage.isBlank()) {
            viewModel.showToast(getString(R.string.feature_feedback_about_link_unavailable))
            return
        }

        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            "market://details?id=$safePackage".toUri()
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        }

        try {
            startActivity(marketIntent)
        } catch (error: ActivityNotFoundException) {
            openUrl("https://play.google.com/store/apps/details?id=$safePackage")
        } catch (error: SecurityException) {
            openUrl("https://play.google.com/store/apps/details?id=$safePackage")
        }
    }

    private fun openUrl(url: String) {
        val safeUrl = url.trim()
        if (!safeUrl.startsWith("https://", ignoreCase = true) &&
            !safeUrl.startsWith("http://", ignoreCase = true)
        ) {
            viewModel.showToast(getString(R.string.feature_feedback_about_link_unavailable))
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, safeUrl.toUri())
        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            viewModel.showToast(getString(R.string.feature_feedback_about_no_handler))
        } catch (error: SecurityException) {
            viewModel.showToast(getString(R.string.feature_feedback_about_no_handler))
        }
    }

    private fun renderUpdateStatus(status: AppUpdateStatus) {
        when (status) {
            AppUpdateStatus.Idle -> Unit
            AppUpdateStatus.Checking -> showCheckingDialog()
            AppUpdateStatus.Latest -> {
                updateDialog?.dismiss()
                viewModel.showToast(getString(R.string.feature_feedback_update_latest_message))
                viewModel.resetUpdateStatus()
            }
            is AppUpdateStatus.UpdateAvailable -> showUpdateDialog(status.info)
            is AppUpdateStatus.Downloading -> showDownloadingDialog(status)
            is AppUpdateStatus.Downloaded -> showDownloadedDialog(status.info, status.file)
            is AppUpdateStatus.Installing -> showInstallingDialog(status.info)
            is AppUpdateStatus.InstallPermissionRequired -> showPermissionDialog(status.info, status.file)
            is AppUpdateStatus.InstallFailed -> showFailureDialog(status.info, status.message)
            is AppUpdateStatus.NetworkError -> showNetworkErrorDialog(status.message)
            is AppUpdateStatus.VerifyFailed -> showFailureDialog(status.info, status.message)
            is AppUpdateStatus.Ignored -> {
                updateDialog?.dismiss()
                viewModel.showToast(getString(R.string.feature_feedback_update_ignored_message))
                viewModel.resetUpdateStatus()
            }
            is AppUpdateStatus.Deferred -> {
                updateDialog?.dismiss()
                viewModel.showToast(getString(R.string.feature_feedback_update_deferred_message))
                viewModel.resetUpdateStatus()
            }
        }
    }

    private fun showCheckingDialog() {
        updateDialog?.dismiss()
        clearDownloadingProgress()
        updateDialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.feature_feedback_update_checking_title)
            .setView(ProgressBar(requireContext()).apply {
                isIndeterminate = true
                setPadding(
                    dimen(CoreUiR.dimen.core_ui_dp_48),
                    dimen(CoreUiR.dimen.core_ui_dp_24),
                    dimen(CoreUiR.dimen.core_ui_dp_48),
                    dimen(CoreUiR.dimen.core_ui_dp_24)
                )
            })
            .setNegativeButton(R.string.feature_feedback_update_action_cancel) { dialog, _ ->
                dialog.dismiss()
                viewModel.resetUpdateStatus()
            }
            .show()
    }

    private fun showUpdateDialog(info: AppUpdateInfo) {
        updateDialog?.dismiss()
        clearDownloadingProgress()
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(
                if (info.forceUpdate) {
                    getString(R.string.feature_feedback_update_force_title)
                } else {
                    getString(R.string.feature_feedback_update_available_title)
                }
            )
            .setView(createUpdateContent(info, progress = null, error = null))
            .setPositiveButton(R.string.feature_feedback_update_action_now, null)
            .setNeutralButton(R.string.feature_feedback_update_action_log, null)
        if (!info.forceUpdate && (info.policy.canDefer || info.policy.canIgnore)) {
            builder.setNegativeButton(R.string.feature_feedback_update_action_later, null)
        }
        updateDialog = builder.show().apply {
            setCanceledOnTouchOutside(!info.forceUpdate)
            setCancelable(!info.forceUpdate)
            getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                viewModel.onUpdateNowClicked()
            }
            getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                viewModel.onViewReleaseLogClicked(info)
            }
            if (!info.forceUpdate && (info.policy.canDefer || info.policy.canIgnore)) {
                getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                    showOptionalChoiceDialog(info)
                }
            }
        }
    }

    private fun showOptionalChoiceDialog(info: AppUpdateInfo) {
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.feature_feedback_update_optional_title)
            .setMessage(R.string.feature_feedback_update_optional_message)
            .setNeutralButton(R.string.feature_feedback_update_action_back, null)
        if (info.policy.canDefer) {
            builder.setPositiveButton(R.string.feature_feedback_update_action_later) { _, _ ->
                viewModel.onRemindLaterClicked()
            }
        }
        if (info.policy.canIgnore) {
            builder.setNegativeButton(R.string.feature_feedback_update_action_ignore) { _, _ ->
                viewModel.onIgnoreVersionClicked()
            }
        }
        builder.show()
    }

    private fun showDownloadingDialog(status: AppUpdateStatus.Downloading) {
        if (downloadingReleaseId == status.info.releaseId && updateDialog?.isShowing == true) {
            updateDownloadingProgress(status.progress)
            return
        }
        updateDialog?.dismiss()
        downloadingReleaseId = status.info.releaseId
        val content = createUpdateContent(status.info, progress = status.progress, error = null)
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.feature_feedback_update_downloading_title)
            .setView(content)
        if (!status.info.forceUpdate && status.info.policy.canDefer) {
            builder.setNegativeButton(R.string.feature_feedback_update_action_later, null)
        }
        updateDialog = builder.show().apply {
            setCanceledOnTouchOutside(!status.info.forceUpdate)
            setCancelable(!status.info.forceUpdate)
            if (!status.info.forceUpdate && status.info.policy.canDefer) {
                getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                    viewModel.onRemindLaterClicked()
                }
            }
        }
    }

    private fun showDownloadedDialog(info: AppUpdateInfo, file: File) {
        updateDialog?.dismiss()
        clearDownloadingProgress()
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.feature_feedback_update_downloaded_title)
            .setView(createUpdateContent(info, progress = 100, error = null))
            .setPositiveButton(R.string.feature_feedback_update_action_install, null)
        if (!info.forceUpdate && info.policy.canDefer) {
            builder.setNegativeButton(R.string.feature_feedback_update_action_later, null)
        }
        updateDialog = builder.show().apply {
                setCanceledOnTouchOutside(!info.forceUpdate)
                setCancelable(!info.forceUpdate)
                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (canInstallPackages()) {
                        installUpdate(info, file)
                    } else {
                        pendingInstall = info to file
                        viewModel.onInstallPermissionRequired(info, file)
                    }
                }
                if (!info.forceUpdate && info.policy.canDefer) {
                    getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                        dismiss()
                        viewModel.resetUpdateStatus()
                    }
                }
            }
    }

    private fun showInstallingDialog(info: AppUpdateInfo) {
        updateDialog?.dismiss()
        clearDownloadingProgress()
        updateDialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.feature_feedback_update_installing_title)
            .setView(createUpdateContent(info, progress = 100, error = null))
            .show()
    }

    private fun showPermissionDialog(info: AppUpdateInfo, file: File) {
        updateDialog?.dismiss()
        clearDownloadingProgress()
        pendingInstall = info to file
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.feature_feedback_update_permission_title)
            .setMessage(R.string.feature_feedback_update_permission_message)
            .setPositiveButton(R.string.feature_feedback_update_action_permission) { _, _ ->
                installPermissionLauncher.launch(createInstallPermissionIntent())
            }
        if (!info.forceUpdate && info.policy.canDefer) {
            builder.setNegativeButton(R.string.feature_feedback_update_action_later, null)
        }
        updateDialog = builder.show().apply {
            setCanceledOnTouchOutside(!info.forceUpdate)
            setCancelable(!info.forceUpdate)
        }
    }

    private fun showFailureDialog(info: AppUpdateInfo?, message: String) {
        updateDialog?.dismiss()
        clearDownloadingProgress()
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.feature_feedback_update_failed_title)
            .setView(info?.let { createUpdateContent(it, progress = null, error = message) }
                ?: TextView(requireContext()).apply {
                    text = message
                    setPadding(
                        dimen(CoreUiR.dimen.core_ui_dp_48),
                        dimen(CoreUiR.dimen.core_ui_dp_24),
                        dimen(CoreUiR.dimen.core_ui_dp_48),
                        dimen(CoreUiR.dimen.core_ui_dp_24)
                    )
                })
            .setPositiveButton(R.string.feature_feedback_update_action_retry) { _, _ ->
                if (info == null) {
                    databind.rowCheckUpdate.performClick()
                } else {
                    viewModel.onUpdateNowClicked()
                }
            }
        if (info?.forceUpdate != true) {
            builder.setNegativeButton(R.string.feature_feedback_update_action_later) { _, _ ->
                viewModel.resetUpdateStatus()
            }
        }
        updateDialog = builder.show().apply {
            setCanceledOnTouchOutside(info?.forceUpdate != true)
            setCancelable(info?.forceUpdate != true)
        }
    }

    private fun showNetworkErrorDialog(message: String) {
        showFailureDialog(null, message)
    }

    private fun createUpdateContent(info: AppUpdateInfo, progress: Int?, error: String?): View {
        val context = requireContext()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dimen(CoreUiR.dimen.core_ui_dp_48),
                dimen(CoreUiR.dimen.core_ui_dp_20),
                dimen(CoreUiR.dimen.core_ui_dp_48),
                dimen(CoreUiR.dimen.core_ui_dp_12)
            )
        }
        content.addView(textLine(getString(R.string.feature_feedback_update_version_span, info.versionSpan), bold = true))
        content.addView(textLine(getString(R.string.feature_feedback_update_publish_time, info.publishedAtMs ?: "--")))
        content.addView(textLine(getString(R.string.feature_feedback_update_package_size, formatBytes(info.fileSizeBytes))))
        content.addView(textLine(getString(R.string.feature_feedback_update_security_hint)))
        content.addView(textLine(getString(R.string.feature_feedback_update_notes_title), bold = true))
        info.releaseNotes.ifEmpty {
            listOf(getString(R.string.feature_feedback_update_notes_empty))
        }.forEachIndexed { index, note ->
            content.addView(textLine("${index + 1}. $note"))
        }
        content.addView(textLine(getString(R.string.feature_feedback_update_risk_title), bold = true))
        val risks = info.riskTips.ifEmpty {
            listOf(getString(R.string.feature_feedback_update_default_risk))
        }
        risks.forEach { tip -> content.addView(textLine(tip)) }
        if (progress != null) {
            val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                this.progress = progress
                setPadding(0, dimen(CoreUiR.dimen.core_ui_dp_18), 0, 0)
            }
            val progressText = textLine(getString(R.string.feature_feedback_update_progress, progress))
            downloadingProgressBar = progressBar
            downloadingProgressText = progressText
            content.addView(progressBar)
            content.addView(progressText)
        }
        if (!error.isNullOrBlank()) {
            content.addView(textLine(getString(R.string.feature_feedback_update_error_prefix, error), isError = true))
        }
        return ScrollView(context).apply { addView(content) }
    }

    private fun updateDownloadingProgress(progress: Int) {
        downloadingProgressBar?.progress = progress
        downloadingProgressText?.text = getString(R.string.feature_feedback_update_progress, progress)
    }

    private fun clearDownloadingProgress() {
        downloadingReleaseId = null
        downloadingProgressBar = null
        downloadingProgressText = null
    }

    private fun textLine(text: String, bold: Boolean = false, isError: Boolean = false): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextSizeFromDimen(
                if (bold) CoreUiR.dimen.core_ui_text_15 else CoreUiR.dimen.core_ui_text_14
            )
            setTextColor(
                when {
                    isError -> 0xFFDC2626.toInt()
                    bold -> 0xFF0F172A.toInt()
                    else -> 0xFF475569.toInt()
                }
            )
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dimen(CoreUiR.dimen.core_ui_dp_8), 0, 0)
            setLineSpacing(dimenFloat(CoreUiR.dimen.core_ui_dp_4), 1f)
        }
    }

    private fun dimen(id: Int): Int {
        return requireContext().dimenPx(id)
    }

    private fun dimenFloat(id: Int): Float {
        return requireContext().dimenPxFloat(id)
    }

    private fun canInstallPackages(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            requireContext().packageManager.canRequestPackageInstalls()
    }

    private fun createInstallPermissionIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${requireContext().packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    private fun installUpdate(info: AppUpdateInfo, file: File) {
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            viewModel.onInstallStarted(info, file)
            startActivity(intent)
        }.onFailure { error ->
            viewModel.onInstallFailed(
                info,
                error.message?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.feature_feedback_update_error_install_failed)
            )
        }
    }

    private fun formatBytes(bytes: Long?): String {
        val value = bytes ?: return "--"
        if (value <= 0L) return "--"
        val mb = value / 1024.0 / 1024.0
        return if (mb >= 1.0) {
            String.format(java.util.Locale.US, "%.1f MB", mb)
        } else {
            "${value / 1024} KB"
        }
    }
}
