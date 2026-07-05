package com.chen.memorizewords.feature.wordbook.plan

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.R as CoreUiR
import com.chen.memorizewords.core.ui.bottomsheetdialogfragment.BaseBottomSheetDialogFragment
import com.chen.memorizewords.core.ui.ext.dimenPx
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.wordbook.model.learning.LearningTestMode
import com.chen.memorizewords.feature.wordbook.databinding.FeatureWordbookDialogStudyModePickerBinding
import com.chen.memorizewords.feature.wordbook.databinding.FeatureWordbookItemStudyModeCardBinding
import com.chen.memorizewords.feature.wordbook.plan.StudyPlanSettingFragment.Companion.KEY_SELECTED_STUDY_MODE
import com.chen.memorizewords.feature.wordbook.plan.StudyPlanSettingFragment.Companion.REQUEST_KEY_STUDY_MODE

class StudyModePickerBottomSheetDialogFragment :
    BaseBottomSheetDialogFragment<BaseViewModel, FeatureWordbookDialogStudyModePickerBinding>() {

    override val viewModel: BaseViewModel by lazy {
        ViewModelProvider(this)[BaseViewModel::class.java]
    }

    private val selectedMode: LearningTestMode
        get() = runCatching {
            LearningTestMode.valueOf(
                requireArguments().getString(ARG_SELECTED_MODE).orEmpty()
            )
        }.getOrDefault(LearningTestMode.MEANING_CHOICE)

    override fun initView(savedInstanceState: Bundle?) {
        databind.modeListContainer.removeAllViews()
        renderModeCards()
    }

    private fun renderModeCards() {
        val context = requireContext()
        StudyPlanSettingViewModel.availableStudyModes().forEachIndexed { index, uiModel ->
            val itemBinding = FeatureWordbookItemStudyModeCardBinding.inflate(
                layoutInflater,
                databind.modeListContainer,
                false
            )
            bindStudyModeCard(
                binding = itemBinding,
                uiModel = uiModel,
                isSelected = uiModel.mode == selectedMode,
                showDefaultBadge = uiModel.mode == selectedMode,
                spec = StudyModeCardSpec.BottomSheet,
                onClick = { dispatchSelection(uiModel.mode) }
            )
            val layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) {
                    topMargin = context.dimenPx(CoreUiR.dimen.core_ui_dp_16)
                }
            }
            databind.modeListContainer.addView(itemBinding.root, layoutParams)
        }
    }

    private fun dispatchSelection(mode: LearningTestMode) {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY_STUDY_MODE,
            bundleOf(KEY_SELECTED_STUDY_MODE to mode.name)
        )
        dismissAllowingStateLoss()
    }

    companion object {
        private const val ARG_SELECTED_MODE = "arg_selected_mode"
        const val TAG = "StudyModePickerBottomSheet"

        fun newInstance(selectedMode: LearningTestMode): StudyModePickerBottomSheetDialogFragment {
            return StudyModePickerBottomSheetDialogFragment().apply {
                arguments = bundleOf(ARG_SELECTED_MODE to selectedMode.name)
            }
        }
    }
}
