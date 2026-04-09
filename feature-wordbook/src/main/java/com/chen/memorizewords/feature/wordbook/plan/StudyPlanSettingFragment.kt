package com.chen.memorizewords.feature.wordbook.plan

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.wordbook.R
import com.chen.memorizewords.feature.wordbook.databinding.FragmentStudyPlanSettingBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StudyPlanSettingFragment :
    BaseFragment<StudyPlanSettingViewModel, FragmentStudyPlanSettingBinding>() {

    override val viewModel: StudyPlanSettingViewModel by lazy {
        ViewModelProvider(this)[StudyPlanSettingViewModel::class.java]
    }

    companion object {
        const val REQUEST_KEY_MODIFY_PLAN = "request_modify_plan"
        const val KEY_PLAN_UPDATED = "key_plan_updated"
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewmodel = viewModel
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
