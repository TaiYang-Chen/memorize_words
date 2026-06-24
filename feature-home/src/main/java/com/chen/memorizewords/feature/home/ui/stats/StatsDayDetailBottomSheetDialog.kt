package com.chen.memorizewords.feature.home.ui.stats

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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

    private val newWordsAdapter = DayWordRecordAdapter(WordRecordStyle.NEW)
    private val reviewWordsAdapter = DayWordRecordAdapter(WordRecordStyle.REVIEW)

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
        databind.ivClose.setOnClickListener {
            dismiss()
        }
        databind.tvNewWordsMore.setOnClickListener {
            viewModel.onNewWordsMoreClicked()
        }
        databind.tvReviewWordsMore.setOnClickListener {
            viewModel.onReviewWordsMoreClicked()
        }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        bottomSheet.post {
            val maxHeight = (resources.displayMetrics.heightPixels * 0.9f).toInt()
            if (bottomSheet.height > maxHeight) {
                bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                    height = maxHeight
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                }
            }
        }
    }

    override fun createObserver() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { ui ->
                        databind.tvDetailDate.text = ui.dateText
                        databind.tvNewCount.text = ui.newCountValue
                        databind.tvReviewCount.text = ui.reviewCountValue
                        databind.tvDuration.text = ui.durationValue
                        databind.tvDurationUnit.text = ui.durationUnit
                        databind.tvPlanStatus.text = ui.planStatusText
                        databind.tvCheckInStatus.text = ui.planStatusSubtitle
                        databind.tvNewSectionTitle.text = ui.newWordsTitle
                        databind.tvReviewSectionTitle.text = ui.reviewWordsTitle
                        databind.tvCheckInStatus.visibility = View.VISIBLE
                        databind.btnMakeUpCheckIn.text = ui.makeUpButtonText
                        databind.checkInActionContainer.visibility =
                            if (ui.showCheckInButton) View.VISIBLE else View.GONE
                        databind.btnMakeUpCheckIn.isEnabled = ui.makeUpButtonEnabled

                        newWordsAdapter.submitList(ui.newWords)
                        reviewWordsAdapter.submitList(ui.reviewWords)
                        databind.tvNewWordsMore.text = ui.newWordsMoreText
                        databind.tvReviewWordsMore.text = ui.reviewWordsMoreText
                        databind.tvNewWordsMore.visibility =
                            if (ui.showNewWordsMore) View.VISIBLE else View.GONE
                        databind.tvReviewWordsMore.visibility =
                            if (ui.showReviewWordsMore) View.VISIBLE else View.GONE

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
