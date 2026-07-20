package com.chen.memorizewords.feature.home.ui.practice

import android.app.Activity
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.core.ui.dialog.prefabricated.ShowConfirmBottomDialog
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.practice.PracticeEntryType
import com.chen.memorizewords.domain.floating.model.FloatingActivationPhase
import com.chen.memorizewords.domain.practice.PracticeMode
import com.chen.memorizewords.domain.practice.usage.EvaluationUsage
import com.chen.memorizewords.domain.practice.usage.PracticeUsageState
import com.chen.memorizewords.feature.home.R
import com.chen.memorizewords.feature.home.databinding.ModuleHomeFragmentPracticeBinding
import com.chen.memorizewords.core.navigation.FloatingWordEntry
import com.chen.memorizewords.core.navigation.FloatingWordDestination
import com.chen.memorizewords.core.navigation.FloatingWordActions
import com.chen.memorizewords.core.navigation.FloatingWordReturnDestination
import com.chen.memorizewords.core.navigation.PracticeEntry
import com.chen.memorizewords.feature.home.ui.profile.ProMembershipActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import coil.dispose
import coil.load
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import java.util.Locale
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PracticeFragment : BaseFragment<PracticeViewModel, ModuleHomeFragmentPracticeBinding>() {

    override val viewModel: PracticeViewModel by lazy {
        ViewModelProvider(this)[PracticeViewModel::class.java]
    }

    @Inject
    lateinit var practiceEntry: PracticeEntry

    @Inject
    lateinit var floatingWordEntry: FloatingWordEntry

    private var pendingMode: PracticeMode? = null
    private var pendingSelectedIds: LongArray? = null
    private var latestFloatingEnabled: Boolean = false
    private var ignoreSwitchUpdate: Boolean = false
    private var latestEvaluationUsage: EvaluationUsage? = null
    private var floatingSetupDialog: BottomSheetDialog? = null
    private var floatingSetupView: View? = null
    private var floatingSetupRequestId: String? = null
    private var pendingQuotaMode: PracticeMode? = null
    private var pendingQuotaSelectedIds: LongArray? = null
    private var pendingQuotaRandomCount: Int = 0

    private val pickWordsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val mode = pendingMode ?: return@registerForActivityResult
        val selectedIds = practiceEntry.extractSelectedWordIds(result.data)
        pendingSelectedIds = selectedIds
        startPractice(mode, selectedIds, randomCount = 0)
        pendingMode = null
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = Settings.canDrawOverlays(requireContext())
        viewModel.onFloatingPermissionResult(granted)
        if (!granted) updateFloatingSwitch(false)
    }

    override fun initView(savedInstanceState: Bundle?) {
        pendingQuotaMode = savedInstanceState?.getString(KEY_PENDING_QUOTA_MODE)?.let { name ->
            runCatching { PracticeMode.valueOf(name) }.getOrNull()
        }
        pendingQuotaSelectedIds = savedInstanceState?.getLongArray(KEY_PENDING_QUOTA_SELECTED_IDS)
        pendingQuotaRandomCount = savedInstanceState?.getInt(KEY_PENDING_QUOTA_RANDOM_COUNT) ?: 0
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
        databind.switchFloatingCard.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreSwitchUpdate) return@setOnCheckedChangeListener
            if (isChecked) {
                updateFloatingSwitch(false)
                viewModel.onFloatingSwitchChecked(Settings.canDrawOverlays(requireContext()))
            } else {
                viewModel.onFloatingEnabledChanged(false)
            }
        }
        databind.btnFloatingSettings.setOnClickListener { viewModel.openFloatingSettings() }
        parentFragmentManager.setFragmentResultListener(
            OVERLAY_PERMISSION_RESULT,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getBoolean(ShowConfirmBottomDialog.RESULT_CONFIRMED)) {
                launchOverlayPermissionSettings()
            } else {
                viewModel.onFloatingPermissionDialogCancelled()
                updateFloatingSwitch(false)
            }
        }
        parentFragmentManager.setFragmentResultListener(
            SHADOWING_QUOTA_RESULT,
            viewLifecycleOwner
        ) { _, result ->
            val mode = pendingQuotaMode
            val selectedIds = pendingQuotaSelectedIds
            val randomCount = pendingQuotaRandomCount
            clearPendingQuotaConfirmation()
            if (
                result.getBoolean(ShowConfirmBottomDialog.RESULT_CONFIRMED) &&
                mode != null
            ) {
                startPractice(
                    mode = mode,
                    selectedIds = selectedIds,
                    randomCount = randomCount,
                    quotaConfirmed = true
                )
            }
        }
    }

    override fun createObserver() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.dashboardUi.collect(::renderDashboard)
                }
                launch {
                    viewModel.floatingEnabled.collect { enabled ->
                        latestFloatingEnabled = enabled
                        updateFloatingSwitch(enabled)
                    }
                }
                launch {
                    viewModel.practiceUsageState.collect(::renderPracticeUsage)
                }
                launch {
                    viewModel.floatingSetupUi.collect { ui ->
                        renderFloatingSetup(ui)
                        syncFloatingSetupDialog(ui)
                    }
                }
            }
        }
    }

    private fun renderDashboard(ui: PracticeDashboardUi) {
        databind.tvPracticeTodayValue.text = ui.todayDurationValue
        databind.tvPracticeTodayUnit.text = ui.todayDurationUnit
        databind.progressPracticeToday.max = PRACTICE_DAILY_GOAL_SECONDS.toInt()
        databind.progressPracticeToday.progress = ui.todayProgress
        databind.tvPracticeStreakValue.text = ui.continuousDaysText
        databind.tvPracticeWeekValue.text = ui.weekDurationValue
        databind.tvPracticeWeekUnit.text = ui.weekDurationUnit
        databind.tvPracticeWeekTrend.text = ui.weekTrendText
        databind.tvPracticeLevelValue.text = ui.levelText
        databind.tvPracticeXp.text = ui.xpText
        databind.progressPracticeXp.max = PRACTICE_XP_PER_LEVEL
        databind.progressPracticeXp.progress = ui.xpProgress
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPracticeUsage()
        if (view != null) {
            updateFloatingSwitch(latestFloatingEnabled)
            viewModel.onFloatingHostResumed(
                canDrawOverlays = Settings.canDrawOverlays(requireContext())
            )
        }
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (val target = event.target) {
            is PracticeViewModel.Route.ToPracticeMode -> showSelectionSheet(target.mode)
            is PracticeViewModel.Route.DispatchFloatingAction -> {
                dispatchFloatingAction(target)
            }
            PracticeViewModel.Route.ToFloatingSettings -> {
                startActivity(
                    floatingWordEntry.createSettingsIntent(
                        context = requireContext(),
                        returnDestination = FloatingWordReturnDestination.PRACTICE
                    )
                )
            }
            is PracticeViewModel.Route.ToCharacterSelection -> {
                floatingSetupDialog?.dismiss()
                startActivity(
                    floatingWordEntry.createSettingsIntent(
                        context = requireContext(),
                        destination = FloatingWordDestination.CHARACTER_SELECTION,
                        activationRequestId = target.activationRequestId,
                        returnDestination = FloatingWordReturnDestination.PRACTICE
                    )
                )
            }
            PracticeViewModel.Route.ToMembership -> {
                startActivity(ProMembershipActivity.createIntent(requireContext()))
            }
            PracticeViewModel.Route.RequestFloatingOverlayPermission -> {
                updateFloatingSwitch(false)
                floatingSetupDialog?.dismiss()
                showOverlayPermissionDialog()
            }
            is PracticeViewModel.Route.ContinueDownloadedActivation -> {
                floatingSetupDialog?.dismiss()
                viewModel.continueDownloadedActivation(
                    requestId = target.activationRequestId,
                    canDrawOverlays = Settings.canDrawOverlays(requireContext())
                )
            }
        }
    }

    private fun dispatchFloatingAction(target: PracticeViewModel.Route.DispatchFloatingAction) {
        try {
            floatingWordEntry.dispatchServiceAction(
                context = requireContext(),
                action = target.action,
                activationRequestId = target.activationRequestId
            )
        } catch (failure: RuntimeException) {
            if (!isExpectedFloatingServiceStartFailure(failure)) throw failure
            if (target.action == FloatingWordActions.ACTION_START) {
                viewModel.onForegroundServiceStartRejected(target.activationRequestId)
            }
            showFloatingDispatchFailureIfNeeded(target.action)
        }
    }
    private fun isExpectedFloatingServiceStartFailure(failure: RuntimeException): Boolean =
        failure is IllegalStateException ||
            failure is SecurityException ||
            (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    Android12ForegroundServiceStartFailure.isForegroundServiceStartNotAllowed(failure)
                )

    private object Android12ForegroundServiceStartFailure {
        fun isForegroundServiceStartNotAllowed(failure: RuntimeException): Boolean =
            failure is ForegroundServiceStartNotAllowedException
    }


    private fun showFloatingDispatchFailureIfNeeded(action: String) {
        if (action != FloatingWordActions.ACTION_START) return
        Toast.makeText(
            requireContext(),
            R.string.feature_home_floating_start_failed,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showSelectionSheet(mode: PracticeMode) {
        PracticeEntrySelectBottomSheet(
            defaultRandomCount = if (mode == PracticeMode.SHADOWING) {
                viewModel.recommendedShadowingCount()
            } else {
                20
            },
            onRandomSelected = { count ->
                pendingSelectedIds = null
                startPractice(mode, selectedIds = null, randomCount = count)
            },
            onSelfSelected = {
                pendingMode = mode
                pickWordsLauncher.launch(
                    practiceEntry.createWordPickerIntent(
                        context = requireContext(),
                        initialSelectedIds = pendingSelectedIds
                    )
                )
            }
        ).show(parentFragmentManager, "practice_entry_select")
    }

    private fun startPractice(
        mode: PracticeMode,
        selectedIds: LongArray?,
        randomCount: Int,
        quotaConfirmed: Boolean = false
    ) {
        val entryType = if (selectedIds != null) {
            PracticeEntryType.SELF
        } else {
            PracticeEntryType.RANDOM
        }
        val entryCount = if (selectedIds != null) {
            selectedIds.size
        } else {
            randomCount
        }.coerceAtLeast(0)
        val remaining = latestEvaluationUsage?.remaining
        if (mode == PracticeMode.SHADOWING && !quotaConfirmed && remaining != null && entryCount > remaining) {
            pendingQuotaMode = mode
            pendingQuotaSelectedIds = selectedIds
            pendingQuotaRandomCount = randomCount
            showConfirmBottomDialog(
                tag = "ShadowingQuotaNotice",
                title = getString(R.string.feature_home_shadowing_quota_notice_title),
                message = getString(R.string.feature_home_shadowing_quota_notice_message, remaining)
            )
            return
        }
        startActivity(
            practiceEntry.createPracticeIntent(
                context = requireContext(),
                modeName = mode.name,
                randomCount = randomCount,
                entryTypeName = entryType.name,
                entryCount = entryCount,
                selectedIds = selectedIds
            )
        )
    }

    private fun renderPracticeUsage(state: PracticeUsageState) {
        val usage = when (state) {
            is PracticeUsageState.Available -> state.usage.evaluation
            is PracticeUsageState.Stale -> state.usage.evaluation
            is PracticeUsageState.Exhausted -> state.usage.evaluation
            else -> null
        }
        latestEvaluationUsage = usage
        databind.cardShadowing.tvModeSubtitle.text = when {
            usage == null -> getString(R.string.feature_home_shadowing_quota_unknown)
            usage.remaining <= 0 -> getString(R.string.feature_home_shadowing_quota_exhausted)
            else -> getString(R.string.feature_home_shadowing_quota_remaining, usage.remaining)
        }
    }

    private fun updateFloatingSwitch(enabled: Boolean) {
        val effectiveEnabled = enabled && Settings.canDrawOverlays(requireContext())
        ignoreSwitchUpdate = true
        databind.switchFloatingCard.isChecked = effectiveEnabled
        databind.switchFloatingCard.isEnabled = !viewModel.floatingSetupUi.value.isBusy
        ignoreSwitchUpdate = false
    }

    private fun showFloatingPetSetup(ui: FloatingPetSetupUi) {
        if (!ui.shouldShowSetupDialog) return
        if (floatingSetupDialog?.isShowing == true) {
            renderFloatingSetup(ui)
            return
        }
        val content = layoutInflater.inflate(
            R.layout.feature_home_dialog_floating_pet_setup,
            null,
            false
        )
        floatingSetupRequestId = ui.requestId
        viewModel.recordFloatingSetupShown(floatingSetupRequestId)
        val dialog = BottomSheetDialog(requireContext()).apply {
            setContentView(content)
            setOnCancelListener {
                if (!viewModel.floatingSetupUi.value.isBusy) {
                    viewModel.cancelFloatingSetup(floatingSetupRequestId)
                }
            }
            setOnDismissListener {
                floatingSetupDialog = null
                floatingSetupView = null
                floatingSetupRequestId = null
            }
        }
        content.findViewById<MaterialButton>(R.id.btnFloatingDownloadEnable)
            .setOnClickListener {
                val ui = viewModel.floatingSetupUi.value
                val requestId = floatingSetupRequestId ?: ui.requestId
                if (ui.hasCharacter) {
                    viewModel.downloadResolvedCharacter(requestId)
                } else {
                    viewModel.retryFloatingCatalog(
                        canDrawOverlays = Settings.canDrawOverlays(requireContext()),
                        expectedRequestId = requestId
                    )
                }
            }
        content.findViewById<MaterialButton>(R.id.btnFloatingChooseCharacter)
            .setOnClickListener { viewModel.openCharacterSelection(floatingSetupRequestId) }
        content.findViewById<MaterialButton>(R.id.btnFloatingNotNow)
            .setOnClickListener {
                viewModel.cancelFloatingSetup(floatingSetupRequestId)
                dialog.dismiss()
            }
        floatingSetupDialog = dialog
        floatingSetupView = content
        renderFloatingSetup(ui)
        dialog.show()
    }

    private fun renderFloatingSetup(ui: FloatingPetSetupUi) {
        if (view != null) {
            databind.switchFloatingCard.isEnabled = !ui.isBusy
            databind.tvFloatingSubtitle.text = when (ui.phase) {
                FloatingActivationPhase.QUEUED ->
                    getString(R.string.feature_home_floating_download_queued)
                FloatingActivationPhase.DOWNLOADING ->
                    getString(R.string.feature_home_floating_downloading, ui.progress)
                FloatingActivationPhase.INSTALLING ->
                    getString(R.string.feature_home_floating_installing)
                FloatingActivationPhase.READY ->
                    getString(R.string.feature_home_floating_ready)
                else -> getString(R.string.feature_home_floating_subtitle)
            }
        }

        floatingSetupRequestId = ui.requestId

        val content = floatingSetupView ?: return
        val characterCard = content.findViewById<View>(R.id.cardFloatingCharacter)
        val preview = content.findViewById<ImageView>(R.id.ivFloatingCharacterPreview)
        val name = content.findViewById<TextView>(R.id.tvFloatingCharacterName)
        val description = content.findViewById<TextView>(R.id.tvFloatingCharacterDescription)
        val size = content.findViewById<TextView>(R.id.tvFloatingCharacterSize)
        val defaultBadge = content.findViewById<TextView>(R.id.tvFloatingCharacterDefaultBadge)
        val progress = content.findViewById<ProgressBar>(R.id.progressFloatingCharacter)
        val status = content.findViewById<TextView>(R.id.tvFloatingCharacterStatus)
        val primary = content.findViewById<MaterialButton>(R.id.btnFloatingDownloadEnable)
        val choose = content.findViewById<MaterialButton>(R.id.btnFloatingChooseCharacter)
        val notNow = content.findViewById<MaterialButton>(R.id.btnFloatingNotNow)

        characterCard.visibility = if (ui.hasCharacter) View.VISIBLE else View.GONE
        name.text = ui.packName
        description.text = ui.description
        description.visibility = if (ui.description.isBlank()) View.GONE else View.VISIBLE
        size.text = formatBytes(ui.sizeBytes)
        defaultBadge.visibility = if (ui.isDefault) View.VISIBLE else View.GONE
        val fallbackPreview = R.drawable.feature_home_bg_floating_ball
        if (!ui.previewUrl.isNullOrBlank()) {
            preview.load(ui.previewUrl) {
                crossfade(true)
                placeholder(fallbackPreview)
                error(fallbackPreview)
                fallback(fallbackPreview)
            }
        } else {
            preview.dispose()
            preview.setImageResource(fallbackPreview)
        }

        progress.visibility = if (ui.isBusy) View.VISIBLE else View.GONE
        progress.progress = ui.progress
        status.visibility = if (
            ui.isBusy ||
            ui.phase == FloatingActivationPhase.FAILED ||
            !ui.hasCharacter
        ) View.VISIBLE else View.GONE
        status.text = when (ui.phase) {
            FloatingActivationPhase.QUEUED ->
                getString(R.string.feature_home_floating_download_queued)
            FloatingActivationPhase.DOWNLOADING ->
                getString(R.string.feature_home_floating_downloading, ui.progress)
            FloatingActivationPhase.INSTALLING ->
                getString(R.string.feature_home_floating_installing)
            FloatingActivationPhase.FAILED ->
                ui.errorMessage ?: getString(R.string.feature_home_floating_download_failed)
            else -> if (!ui.hasCharacter) {
                getString(R.string.feature_home_floating_no_character)
            } else {
                ""
            }
        }
        primary.isEnabled = !ui.isBusy
        primary.text = when {
            !ui.hasCharacter -> getString(R.string.feature_home_floating_retry_catalog)
            ui.phase == FloatingActivationPhase.FAILED ->
                getString(R.string.feature_home_floating_retry)
            else -> getString(
                R.string.feature_home_floating_download_enable,
                formatBytes(ui.sizeBytes)
            )
        }
        choose.isEnabled = !ui.isBusy
        notNow.text = if (ui.isBusy) {
            getString(R.string.feature_home_floating_cancel_download)
        } else {
            getString(R.string.feature_home_floating_not_now)
        }
        floatingSetupDialog?.setCancelable(!ui.isBusy)
        floatingSetupDialog?.setCanceledOnTouchOutside(!ui.isBusy)

    }

    private fun syncFloatingSetupDialog(ui: FloatingPetSetupUi) {
        if (!ui.shouldShowSetupDialog) {
            if (floatingSetupDialog?.isShowing == true) floatingSetupDialog?.dismiss()
            return
        }
        if (
            parentFragmentManager.findFragmentByTag(TAG_OVERLAY_PERMISSION) == null &&
            viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            showFloatingPetSetup(ui)
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "--"
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    override fun onDestroyView() {
        floatingSetupDialog?.dismiss()
        floatingSetupDialog = null
        floatingSetupView = null
        floatingSetupRequestId = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingQuotaMode?.let { outState.putString(KEY_PENDING_QUOTA_MODE, it.name) }
        outState.putLongArray(KEY_PENDING_QUOTA_SELECTED_IDS, pendingQuotaSelectedIds)
        outState.putInt(KEY_PENDING_QUOTA_RANDOM_COUNT, pendingQuotaRandomCount)
        super.onSaveInstanceState(outState)
    }

    private fun showOverlayPermissionDialog() {
        if (parentFragmentManager.findFragmentByTag(TAG_OVERLAY_PERMISSION) != null) return
        ShowConfirmBottomDialog.newInstance(
            data = UiEvent.Dialog.ConfirmBottom(
                title = getString(R.string.feature_home_floating_permission_title),
                message = getString(R.string.feature_home_floating_permission_message),
                confirmText = getString(R.string.feature_home_floating_permission_confirm),
                cancelText = getString(R.string.feature_home_floating_permission_cancel)
            ),
            resultKey = OVERLAY_PERMISSION_RESULT
        ).show(parentFragmentManager, TAG_OVERLAY_PERMISSION)
    }

    private fun launchOverlayPermissionSettings() {
        overlayPermissionLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
            )
        )
    }

    private fun showConfirmBottomDialog(
        tag: String,
        title: String,
        message: String
    ) {
        if (parentFragmentManager.findFragmentByTag(tag) != null) return
        ShowConfirmBottomDialog.newInstance(
            data = UiEvent.Dialog.ConfirmBottom(
                title = title,
                message = message
            ),
            resultKey = SHADOWING_QUOTA_RESULT
        ).show(parentFragmentManager, tag)
    }

    private fun clearPendingQuotaConfirmation() {
        pendingQuotaMode = null
        pendingQuotaSelectedIds = null
        pendingQuotaRandomCount = 0
    }

    private companion object {
        const val TAG_OVERLAY_PERMISSION = "FloatingOverlayPermissionDialog"
        const val OVERLAY_PERMISSION_RESULT = "FloatingOverlayPermissionResult"
        const val SHADOWING_QUOTA_RESULT = "ShadowingQuotaResult"
        const val KEY_PENDING_QUOTA_MODE = "pending_quota_mode"
        const val KEY_PENDING_QUOTA_SELECTED_IDS = "pending_quota_selected_ids"
        const val KEY_PENDING_QUOTA_RANDOM_COUNT = "pending_quota_random_count"
    }
}
