package com.chen.memorizewords.feature.learning.ui.practice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.FragmentPracticeShadowingDoneBinding

class ShadowingPracticeDoneFragment : Fragment() {

    private var _binding: FragmentPracticeShadowingDoneBinding? = null
    private val binding: FragmentPracticeShadowingDoneBinding
        get() = requireNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPracticeShadowingDoneBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val questionCount = arguments?.getInt(ARG_QUESTION_COUNT).orZero()
        val completedCount = arguments?.getInt(ARG_COMPLETED_COUNT).orZero()
        val correctCount = arguments?.getInt(ARG_CORRECT_COUNT).orZero()
        val submitCount = arguments?.getInt(ARG_SUBMIT_COUNT).orZero()

        binding.tvMetricCompletedValue.text = getString(
            R.string.practice_shadowing_done_completed_format,
            completedCount,
            questionCount
        )
        binding.tvMetricRecordingsValue.text = submitCount.toString()
        binding.tvMetricPassedValue.text = correctCount.toString()
        binding.tvDoneSubtitle.text = resources.getQuantityString(
            R.plurals.practice_shadowing_done_subtitle,
            completedCount,
            completedCount
        )
        binding.btnClose.setOnClickListener { finishPractice() }
        binding.btnDone.setOnClickListener { finishPractice() }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun finishPractice() {
        requireActivity().finish()
    }

    private fun Int?.orZero(): Int = this ?: 0

    companion object {
        const val TAG = "shadowing_practice_done"

        private const val ARG_QUESTION_COUNT = "question_count"
        private const val ARG_COMPLETED_COUNT = "completed_count"
        private const val ARG_CORRECT_COUNT = "correct_count"
        private const val ARG_SUBMIT_COUNT = "submit_count"

        fun newInstance(
            questionCount: Int,
            completedCount: Int,
            correctCount: Int,
            submitCount: Int
        ): ShadowingPracticeDoneFragment = ShadowingPracticeDoneFragment().apply {
            arguments = bundleOf(
                ARG_QUESTION_COUNT to questionCount,
                ARG_COMPLETED_COUNT to completedCount,
                ARG_CORRECT_COUNT to correctCount,
                ARG_SUBMIT_COUNT to submitCount
            )
        }
    }
}
