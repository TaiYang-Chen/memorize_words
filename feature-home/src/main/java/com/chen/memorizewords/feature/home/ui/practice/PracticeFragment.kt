package com.chen.memorizewords.feature.home.ui.practice

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.core.ui.dialog.prefabricated.ShowConfirmBottomDialog
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.practice.PracticeEntryType
import com.chen.memorizewords.domain.practice.PracticeMode
import com.chen.memorizewords.feature.home.R
import com.chen.memorizewords.feature.home.databinding.ModuleHomeFragmentPracticeBinding
import com.chen.memorizewords.core.navigation.FloatingWordEntry
import com.chen.memorizewords.core.navigation.PracticeEntry
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
    private var latestFloatingEnabled: Boolean = false
    private var ignoreSwitchUpdate: Boolean = false

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
        if (Settings.canDrawOverlays(requireContext())) {
            viewModel.onFloatingEnabledChanged(true)
        } else {
            updateFloatingSwitch(false)
        }
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
        databind.switchFloatingCard.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreSwitchUpdate) return@setOnCheckedChangeListener
            if (isChecked) {
                if (Settings.canDrawOverlays(requireContext())) {
                    viewModel.onFloatingEnabledChanged(true)
                } else {
                    updateFloatingSwitch(false)
                    showOverlayPermissionDialog()
                }
            } else {
                viewModel.onFloatingEnabledChanged(false)
            }
        }
        databind.btnFloatingSettings.setOnClickListener { openFloatingSettings() }
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
        if (view != null) {
            updateFloatingSwitch(latestFloatingEnabled)
        }
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (val target = event.target) {
            is PracticeViewModel.Route.ToPracticeMode -> showSelectionSheet(target.mode)
            is PracticeViewModel.Route.DispatchFloatingAction -> {
                floatingWordEntry.dispatchServiceAction(requireContext(), target.action)
            }
        }
    }

    private fun showSelectionSheet(mode: PracticeMode) {
        PracticeEntrySelectBottomSheet(
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
        randomCount: Int
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

    private fun openFloatingSettings() {
        startActivity(floatingWordEntry.createSettingsIntent(requireContext()))
    }

    private fun updateFloatingSwitch(enabled: Boolean) {
        val effectiveEnabled = enabled && Settings.canDrawOverlays(requireContext())
        ignoreSwitchUpdate = true
        databind.switchFloatingCard.isChecked = effectiveEnabled
        ignoreSwitchUpdate = false
    }

    private fun showOverlayPermissionDialog() {
        showConfirmBottomDialog(
            tag = "FloatingOverlayPermissionDialog",
            title = getString(R.string.feature_home_floating_permission_title),
            message = getString(R.string.feature_home_floating_permission_message)
        ) {
            overlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")
                )
            )
        }
    }

    private fun showConfirmBottomDialog(
        tag: String,
        title: String,
        message: String,
        onConfirm: (() -> Unit)? = null
    ) {
        if (parentFragmentManager.findFragmentByTag(tag) != null) return
        ShowConfirmBottomDialog(
            UiEvent.Dialog.ConfirmBottom(
                title = title,
                message = message
            ),
            onConfirm = onConfirm
        ).show(parentFragmentManager, tag)
    }
}
