package com.chen.memorizewords.feature.home.ui.stats

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.chen.memorizewords.core.ui.bottomsheetdialogfragment.BaseBottomSheetDialogFragment
import com.chen.memorizewords.feature.home.databinding.ModuleHomeDialogStatsDayDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StatsDayDetailBottomSheetDialog :
    BaseBottomSheetDialogFragment<StatsDayDetailViewModel, ModuleHomeDialogStatsDayDetailBinding>() {

    override val viewModel: StatsDayDetailViewModel by lazy {
        ViewModelProvider(this)[StatsDayDetailViewModel::class.java]
    }

    private val newWordsAdapter = DayWordRecordAdapter()
    private val reviewWordsAdapter = DayWordRecordAdapter()

    override fun initView(savedInstanceState: Bundle?) {
        databind.rvNewWords.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = newWordsAdapter
            itemAnimator = null
        }
        databind.rvReviewWords.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = reviewWordsAdapter
            itemAnimator = null
        }
        databind.btnMakeUpCheckIn.setOnClickListener {
            viewModel.onMakeUpClicked()
        }
    }

    override fun createObserver() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { ui ->
                        databind.tvDetailDate.text = ui.dateText
                        databind.tvNewCount.text = ui.newCountText
                        databind.tvReviewCount.text = ui.reviewCountText
                        databind.tvDuration.text = ui.durationText
                        databind.tvPlanStatus.text = ui.planStatusText
                        databind.tvCheckInStatus.text = ui.checkInStatusText
                        databind.tvCheckInStatus.visibility =
                            if (ui.showCheckInStatus) View.VISIBLE else View.GONE
                        databind.btnMakeUpCheckIn.text = ui.makeUpButtonText
                        databind.checkInActionContainer.visibility =
                            if (ui.showCheckInButton) View.VISIBLE else View.GONE
                        databind.btnMakeUpCheckIn.isEnabled = ui.makeUpButtonEnabled

                        newWordsAdapter.submitList(ui.newWords)
                        reviewWordsAdapter.submitList(ui.reviewWords)

                        databind.tvDayEmpty.visibility = if (ui.isEmptyDay) View.VISIBLE else View.GONE
                        databind.tvNewEmpty.visibility = if (ui.newWords.isEmpty()) View.VISIBLE else View.GONE
                        databind.tvReviewEmpty.visibility =
                            if (ui.reviewWords.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "StatsDayDetailBottomSheetDialog"

        fun newInstance(date: String): StatsDayDetailBottomSheetDialog {
            return StatsDayDetailBottomSheetDialog().apply {
                arguments = bundleOf(StatsDayDetailViewModel.ARG_DATE to date)
            }
        }
    }
}
