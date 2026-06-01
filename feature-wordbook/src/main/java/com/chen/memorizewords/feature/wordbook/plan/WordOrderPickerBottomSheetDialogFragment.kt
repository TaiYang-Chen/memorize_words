package com.chen.memorizewords.feature.wordbook.plan

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.bottomsheetdialogfragment.BaseBottomSheetDialogFragment
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.wordbook.repository.WordOrderType
import com.chen.memorizewords.feature.wordbook.R
import com.chen.memorizewords.feature.wordbook.databinding.FeatureWordbookDialogWordOrderPickerBinding
import com.chen.memorizewords.feature.wordbook.databinding.FeatureWordbookItemWordOrderOptionBinding
import com.chen.memorizewords.feature.wordbook.plan.StudyPlanSettingFragment.Companion.KEY_SELECTED_WORD_ORDER
import com.chen.memorizewords.feature.wordbook.plan.StudyPlanSettingFragment.Companion.REQUEST_KEY_WORD_ORDER

class WordOrderPickerBottomSheetDialogFragment :
    BaseBottomSheetDialogFragment<BaseViewModel, FeatureWordbookDialogWordOrderPickerBinding>() {

    override val viewModel: BaseViewModel by lazy {
        ViewModelProvider(this)[BaseViewModel::class.java]
    }

    private val selectedWordOrderType: WordOrderType
        get() = runCatching {
            WordOrderType.valueOf(
                requireArguments().getString(ARG_SELECTED_WORD_ORDER).orEmpty()
            )
        }.getOrDefault(WordOrderType.RANDOM)

    override fun initView(savedInstanceState: Bundle?) {
        databind.orderListContainer.removeAllViews()
        renderOrderOptions()
    }

    private fun renderOrderOptions() {
        val context = requireContext()
        WordOrderType.entries.forEachIndexed { index, wordOrderType ->
            val itemBinding = FeatureWordbookItemWordOrderOptionBinding.inflate(
                layoutInflater,
                databind.orderListContainer,
                false
            )
            val isSelected = wordOrderType == selectedWordOrderType
            itemBinding.tvOrderTitle.setText(
                StudyPlanSettingViewModel.wordOrderLabelRes(wordOrderType)
            )
            itemBinding.ivSelected.visibility =
                if (isSelected) android.view.View.VISIBLE else android.view.View.GONE
            itemBinding.optionCard.setCardBackgroundColor(
                ContextCompat.getColor(
                    context,
                    if (isSelected) {
                        R.color.feature_wordbook_study_plan_option_selected_background
                    } else {
                        R.color.feature_wordbook_study_plan_option_unselected_background
                    }
                )
            )
            itemBinding.optionCard.strokeColor = ContextCompat.getColor(
                context,
                if (isSelected) {
                    R.color.feature_wordbook_study_plan_option_selected_stroke
                } else {
                    R.color.feature_wordbook_study_plan_option_unselected_stroke
                }
            )
            itemBinding.optionCard.setOnClickListener {
                dispatchSelection(wordOrderType)
            }
            val layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) {
                    topMargin = (12 * context.resources.displayMetrics.density).toInt()
                }
            }
            databind.orderListContainer.addView(itemBinding.root, layoutParams)
        }
    }

    private fun dispatchSelection(wordOrderType: WordOrderType) {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY_WORD_ORDER,
            bundleOf(KEY_SELECTED_WORD_ORDER to wordOrderType.name)
        )
        dismissAllowingStateLoss()
    }

    companion object {
        private const val ARG_SELECTED_WORD_ORDER = "arg_selected_word_order"
        const val TAG = "WordOrderPickerBottomSheet"

        fun newInstance(selectedWordOrderType: WordOrderType): WordOrderPickerBottomSheetDialogFragment {
            return WordOrderPickerBottomSheetDialogFragment().apply {
                arguments = bundleOf(ARG_SELECTED_WORD_ORDER to selectedWordOrderType.name)
            }
        }
    }
}
