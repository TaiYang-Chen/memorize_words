package com.chen.memorizewords.feature.wordbook.plan

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.wordbook.model.learning.LearningTestMode
import com.chen.memorizewords.domain.wordbook.repository.WordOrderType
import com.chen.memorizewords.feature.wordbook.R
import com.chen.memorizewords.feature.wordbook.databinding.FragmentStudyPlanSettingBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StudyPlanSettingFragment :
    BaseFragment<StudyPlanSettingViewModel, FragmentStudyPlanSettingBinding>() {

    override val viewModel: StudyPlanSettingViewModel by lazy {
        ViewModelProvider(this)[StudyPlanSettingViewModel::class.java]
    }

    companion object {
        const val REQUEST_KEY_MODIFY_PLAN = "request_modify_plan"
        const val KEY_PLAN_UPDATED = "key_plan_updated"
        const val REQUEST_KEY_STUDY_MODE = "request_study_mode"
        const val KEY_SELECTED_STUDY_MODE = "key_selected_study_mode"
        const val REQUEST_KEY_WORD_ORDER = "request_word_order"
        const val KEY_SELECTED_WORD_ORDER = "key_selected_word_order"
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.lifecycleOwner = viewLifecycleOwner
        databind.viewmodel = viewModel
        databind.wordBookCardState = viewModel.wordBookCardState.value
        parentFragmentManager.setFragmentResultListener(
            REQUEST_KEY_STUDY_MODE,
            viewLifecycleOwner
        ) { _, bundle ->
            val mode = runCatching {
                LearningTestMode.valueOf(
                    bundle.getString(KEY_SELECTED_STUDY_MODE).orEmpty()
                )
            }.getOrNull() ?: return@setFragmentResultListener
            viewModel.onSelectTestMode(mode)
        }
        parentFragmentManager.setFragmentResultListener(
            REQUEST_KEY_WORD_ORDER,
            viewLifecycleOwner
        ) { _, bundle ->
            val wordOrderType = runCatching {
                WordOrderType.valueOf(
                    bundle.getString(KEY_SELECTED_WORD_ORDER).orEmpty()
                )
            }.getOrNull() ?: return@setFragmentResultListener
            viewModel.onSelectWordOrderType(wordOrderType)
        }
        databind.layoutStudyModeAction.setOnClickListener {
            viewModel.onSelectTestMode(LearningTestMode.MEANING_CHOICE)
        }
        databind.layoutStudyModeAction.isClickable = false
        databind.layoutStudyModeAction.isFocusable = false
        databind.layoutWordOrderAction.setOnClickListener {
            showWordOrderPicker()
        }
    }

    override fun createObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.wordBookCardState.collect { state ->
                        databind.wordBookCardState = state
                    }
                }
                launch {
                    viewModel.currentStudyModeCardState.collect { uiModel ->
                        databind.tvStudyModeValue.setText(uiModel.titleRes)
                    }
                }
                launch {
                    viewModel.currentWordOrderLabelRes.collect { titleRes ->
                        databind.tvWordOrderValue.setText(titleRes)
                    }
                }
            }
        }
    }

    private fun showStudyModePicker() {
        if (parentFragmentManager.findFragmentByTag(StudyModePickerBottomSheetDialogFragment.TAG) != null) {
            return
        }
        StudyModePickerBottomSheetDialogFragment
            .newInstance(viewModel.planCountCardState.value.testMode)
            .show(parentFragmentManager, StudyModePickerBottomSheetDialogFragment.TAG)
    }

    private fun showWordOrderPicker() {
        if (parentFragmentManager.findFragmentByTag(WordOrderPickerBottomSheetDialogFragment.TAG) != null) {
            return
        }
        WordOrderPickerBottomSheetDialogFragment
            .newInstance(viewModel.planCountCardState.value.wordOrderType)
            .show(parentFragmentManager, WordOrderPickerBottomSheetDialogFragment.TAG)
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (val target = event.target) {
            is StudyPlanSettingViewModel.Route.ToWordList -> {
                findNavController().navigate(
                    R.id.action_studyPlan_to_wordList,
                    Bundle().apply { putLong("bookId", target.bookId) }
                )
            }

            StudyPlanSettingViewModel.Route.ToMyWordBooks -> {
                findNavController().navigate(R.id.action_studyPlan_to_myWordBooks)
            }

            is StudyPlanSettingViewModel.Route.ToModifyPlan -> {
                findNavController().navigate(
                    R.id.action_studyPlan_to_modifyPlan,
                    Bundle().apply {
                        putInt("newCount", target.newCount)
                        putInt("reviewCount", target.reviewCount)
                    }
                )
            }
        }
    }

    override fun onConfirmDialog(event: UiEvent.Dialog.Confirm) {
        if (event.action == StudyPlanSettingViewModel.ACTION_RESET_BOOK_WORDS) {
            viewModel.onResetBookWordConfirmed()
            return
        }
        super.onConfirmDialog(event)
    }
}
