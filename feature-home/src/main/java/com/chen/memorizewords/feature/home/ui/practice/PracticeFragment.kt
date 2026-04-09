package com.chen.memorizewords.feature.home.ui.practice

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.chen.memorizewords.core.ui.dialog.prefabricated.ShowConfirmBottomDialog
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.model.practice.PracticeEntryType
import com.chen.memorizewords.domain.model.practice.PracticeMode
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
        if (Settings.canDrawOverlays(requireContext())) {
            viewModel.onFloatingEnabledChanged(true)
        } else {
            updateFloatingSwitch(false)
        }
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
        databind.rvPracticeRecords.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recordAdapter
            itemAnimator = null
        }
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
                    viewModel.todayDurationHmsText.collect { databind.tvPracticeTodayDuration.text = it }
                }
                launch {
                    viewModel.totalDurationMinutesText.collect {
                        databind.tvPracticeTotalMinutesValue.text = it
                    }
                }
                launch {
                    viewModel.continuousDaysText.collect { databind.tvPracticeStreakValue.text = it }
                }
                launch {
                    viewModel.recentRecords.collect { records ->
                        recordAdapter.submitList(records)
                        databind.tvPracticeRecordsEmpty.visibility =
                            if (records.isEmpty()) View.VISIBLE else View.GONE
                    }
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
                mode = mode,
                randomCount = randomCount,
                entryType = entryType,
                entryCount = entryCount,
                selectedIds = selectedIds
            )
        )
    }

    private fun showRecordDetail(recordId: Long) {
        val tag = PracticeRecordDetailBottomSheetDialog.TAG
        val existing = childFragmentManager.findFragmentByTag(tag)
        if (existing != null) return
        PracticeRecordDetailBottomSheetDialog.newInstance(recordId)
            .show(childFragmentManager, tag)
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
