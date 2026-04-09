package com.chen.memorizewords.feature.home.ui.practice

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.chen.memorizewords.core.ui.bottomsheetdialogfragment.BaseBottomSheetDialogFragment
import com.chen.memorizewords.feature.home.databinding.ModuleHomeDialogPracticeRecordDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PracticeRecordDetailBottomSheetDialog :
    BaseBottomSheetDialogFragment<PracticeRecordDetailViewModel, ModuleHomeDialogPracticeRecordDetailBinding>() {

    override val viewModel: PracticeRecordDetailViewModel by lazy {
        ViewModelProvider(this)[PracticeRecordDetailViewModel::class.java]
    }

    private val wordAdapter = PracticeRecordWordAdapter()

    override fun initView(savedInstanceState: Bundle?) {
        databind.rvRecordWords.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = wordAdapter
            itemAnimator = null
        }
        val recordId = arguments?.getLong(ARG_RECORD_ID) ?: return
        viewModel.load(recordId)
    }

    override fun createObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        databind.tvDetailRecordTitle.text = state.recordTitle
                        databind.tvDetailRecordSubtitle.text = state.recordSubtitle
                        databind.tvDetailRecordSummary.isVisible = state.summaryText.isNotBlank()
                        databind.tvDetailRecordSummary.text = state.summaryText
                        wordAdapter.submitList(state.words)
                        databind.tvRecordWordsEmpty.visibility =
                            if (state.isEmpty) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "PracticeRecordDetailBottomSheetDialog"
        private const val ARG_RECORD_ID = "arg_record_id"

        fun newInstance(recordId: Long): PracticeRecordDetailBottomSheetDialog {
            return PracticeRecordDetailBottomSheetDialog().apply {
                arguments = bundleOf(ARG_RECORD_ID to recordId)
            }
        }
    }
}
