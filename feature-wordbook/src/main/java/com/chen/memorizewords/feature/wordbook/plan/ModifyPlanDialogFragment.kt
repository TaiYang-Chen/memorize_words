package com.chen.memorizewords.feature.wordbook.plan

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import com.chen.memorizewords.core.ui.bottomsheetdialogfragment.BaseBottomSheetDialogFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.wordbook.databinding.DialogWordbookModifyPlanBinding
import com.chen.memorizewords.feature.wordbook.plan.StudyPlanSettingFragment.Companion.KEY_PLAN_UPDATED
import com.chen.memorizewords.feature.wordbook.plan.StudyPlanSettingFragment.Companion.REQUEST_KEY_MODIFY_PLAN
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ModifyPlanDialogFragment :
    BaseBottomSheetDialogFragment<ModifyPlanDialogViewModel,
            DialogWordbookModifyPlanBinding>() {

    override val viewModel: ModifyPlanDialogViewModel by lazy {
        ViewModelProvider(this)[ModifyPlanDialogViewModel::class.java]
    }

    private val args: ModifyPlanDialogFragmentArgs by navArgs()

    override fun initView(savedInstanceState: Bundle?) {
        databind.lifecycleOwner = viewLifecycleOwner
        databind.vm = viewModel

        viewModel.loadPlan(args.newCount, args.reviewCount)
    }

    override fun consumeUiEvent(event: UiEvent): Boolean {
        return when (event) {
            is UiEvent.Navigation.Back -> {
                notifyPlanUpdatedAndClose()
                true
            }

            else -> false
        }
    }

    private fun notifyPlanUpdatedAndClose() {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY_MODIFY_PLAN,
            bundleOf(KEY_PLAN_UPDATED to true)
        )
        dismiss()
    }
}
