package com.chen.memorizewords.feature.home.ui.practice

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.chen.memorizewords.core.ui.dialog.prefabricated.ShowConfirmBottomDialog
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.practice.PracticeEntryType
import com.chen.memorizewords.domain.practice.PracticeMode
import com.chen.memorizewords.domain.practice.usage.EvaluationUsage
import com.chen.memorizewords.domain.practice.usage.PracticeUsageState
import com.chen.memorizewords.feature.home.R
import com.chen.memorizewords.feature.home.databinding.ModuleHomeFragmentPracticeBinding
import com.chen.memorizewords.core.navigation.FloatingWordEntry
import com.chen.memorizewords.core.navigation.FloatingWordDestination
import com.chen.memorizewords.core.navigation.FloatingWordReturnDestination
import com.chen.memorizewords.core.navigation.PracticeEntry
import com.chen.memorizewords.feature.home.ui.profile.ProMembershipActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
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
    private var latestFloatingRuntimeUi = FloatingRuntimeUi()
    private var ignoreSwitchUpdate: Boolean = false
    private var latestEvaluationUsage: EvaluationUsage? = null
    private var pendingQuotaMode: PracticeMode? = null
    private var pendingQuotaSelectedIds: LongArray? = null
    private var pendingQuotaRandomCount: Int = 0
    private val recordAdapter = PracticeRecordAdapter { record ->
        showRecordDetail(record.id)
    }

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
    }

    override fun initView(savedInstanceState: Bundle?) {
        pendingQuotaMode = savedInstanceState?.getString(KEY_PENDING_QUOTA_MODE)?.let { name ->
            runCatching { PracticeMode.valueOf(name) }.getOrNull()
        }
        pendingQuotaSelectedIds = savedInstanceState?.getLongArray(KEY_PENDING_QUOTA_SELECTED_IDS)
        pendingQuotaRandomCount = savedInstanceState?.getInt(KEY_PENDING_QUOTA_RANDOM_COUNT) ?: 0
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
        databind.rvPracticeRecords.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recordAdapter
            itemAnimator = null
            isNestedScrollingEnabled = false
        }
        databind.switchFloatingCard.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreSwitchUpdate) return@setOnCheckedChangeListener
            if (isChecked) {
                viewModel.onFloatingSwitchChecked()
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
                    viewModel.floatingRuntimeUi.collect(::renderFloatingRuntime)
                }
                launch {
                    viewModel.practiceUsageState.collect(::renderPracticeUsage)
                }
                launch {
                    viewModel.recentRecords.collect { records ->
                        recordAdapter.submitList(records)
                        databind.layoutPracticeRecordsEmpty.isVisible = records.isEmpty()
                        databind.rvPracticeRecords.isVisible = records.isNotEmpty()
                    }
                }
            }
        }
    }

    private fun renderDashboard(ui: PracticeDashboardUi) {
        databind.tvPracticeTodayValue.text = ui.todayDurationValue
        databind.tvPracticeTodayUnit.text = ui.todayDurationUnit
        databind.tvPracticeGoalProgress.text = SpannableString(ui.todayProgressText).apply {
            val valueEnd = ui.todayProgressText.indexOf(" /").takeIf { it > 0 } ?: 0
            if (valueEnd > 0) {
                setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.feature_home_practice_v2_green_dark
                        )
                    ),
                    0,
                    valueEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        databind.tvPracticeGoalPercent.text = ui.todayPercentText
        databind.progressPracticeToday.max = PRACTICE_DAILY_GOAL_SECONDS.toInt()
        databind.progressPracticeToday.progress = ui.todayProgress
        databind.tvPracticeStreakValue.text = ui.continuousDaysText
        databind.tvPracticeWeekValue.text = ui.weekDurationValue
        databind.tvPracticeWeekUnit.text = ui.weekDurationUnit
        databind.tvPracticeWeekTrend.text = ui.weekTrendText
        renderWeekTrend(ui.weekTrendDirection)
        databind.tvPracticeLevelValue.text = ui.levelText
        databind.tvPracticeXp.text = ui.xpText
        databind.progressPracticeXp.max = PRACTICE_XP_PER_LEVEL
        databind.progressPracticeXp.progress = ui.xpProgress
    }

    private fun renderWeekTrend(direction: PracticeTrendDirection) {
        val (iconRes, colorRes) = when (direction) {
            PracticeTrendDirection.UP -> {
                R.drawable.feature_home_practice_v2_ic_trend_up to R.color.feature_home_practice_v2_green
            }
            PracticeTrendDirection.DOWN -> {
                R.drawable.feature_home_practice_v2_ic_trend_down to R.color.feature_home_practice_v2_red
            }
            PracticeTrendDirection.FLAT -> {
                R.drawable.feature_home_practice_v2_ic_trend_flat to R.color.feature_home_practice_v2_text_secondary
            }
        }
        val color = ContextCompat.getColor(requireContext(), colorRes)
        databind.tvPracticeWeekTrend.setTextColor(color)
        databind.ivPracticeWeekTrend.apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(color)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPracticeUsage()
        if (view != null) {
            renderFloatingRuntime(latestFloatingRuntimeUi)
            viewModel.onFloatingHostResumed()
        }
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (val target = event.target) {
            is PracticeViewModel.Route.ToPracticeMode -> showSelectionSheet(target.mode)
            PracticeViewModel.Route.ToFloatingSettings -> {
                startActivity(
                    floatingWordEntry.createSettingsIntent(
                        context = requireContext(),
                        returnDestination = FloatingWordReturnDestination.PRACTICE
                    )
                )
            }
            PracticeViewModel.Route.ToCharacterSelection -> {
                startActivity(
                    floatingWordEntry.createSettingsIntent(
                        context = requireContext(),
                        destination = FloatingWordDestination.CHARACTER_SELECTION,
                        returnDestination = FloatingWordReturnDestination.PRACTICE
                    )
                )
            }
            PracticeViewModel.Route.ToMembership -> {
                startActivity(ProMembershipActivity.createIntent(requireContext()))
            }
            PracticeViewModel.Route.RequestFloatingOverlayPermission -> {
                showOverlayPermissionDialog()
            }
        }
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
        val quotaBadge = buildPracticeQuotaBadgeUi(usage)
        databind.cardShadowing.tvModeBadge.text = if (quotaBadge.remaining == null) {
            getString(quotaBadge.textRes)
        } else {
            getString(quotaBadge.textRes, quotaBadge.remaining)
        }
    }

    private fun showRecordDetail(recordId: Long) {
        val tag = PracticeRecordDetailBottomSheetDialog.TAG
        if (childFragmentManager.findFragmentByTag(tag) != null) return
        PracticeRecordDetailBottomSheetDialog.newInstance(recordId)
            .show(childFragmentManager, tag)
    }

    private fun renderFloatingRuntime(ui: FloatingRuntimeUi) {
        latestFloatingRuntimeUi = ui
        ignoreSwitchUpdate = true
        databind.switchFloatingCard.isChecked = ui.checked
        databind.switchFloatingCard.isEnabled = ui.switchEnabled
        databind.tvFloatingSubtitle.text = ui.subtitle
        ignoreSwitchUpdate = false
    }

    override fun onDestroyView() {
        databind.rvPracticeRecords.adapter = null
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
