package com.chen.memorizewords.feature.learning.ui.done

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.chen.memorizewords.core.ui.ext.dpToPx
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.learning.LinearSpacingItemDecoration
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.FragmentLearningDoneBinding
import com.chen.memorizewords.core.navigation.LearningEntry
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LearningDoneFragment :
    BaseFragment<LearningDoneViewModel, FragmentLearningDoneBinding>() {

    override val viewModel: LearningDoneViewModel by lazy {
        ViewModelProvider(this)[LearningDoneViewModel::class.java]
    }

    @Inject
    lateinit var learningEntry: LearningEntry

    private val args: LearningDoneFragmentArgs by navArgs()

    private val adapter = WordPagingAdapter { item ->
        findNavController().navigate(
            R.id.action_global_wordEntryDetailFragment,
            bundleOf(
                "wordId" to item.wordId,
                "wordText" to item.word
            )
        )
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner

        databind.btnContinue.setOnClickListener { viewModel.onContinueLearning() }
        databind.btnBackHome.setOnClickListener { viewModel.finish() }

        val spacing = LinearSpacingItemDecoration(10.dpToPx(requireContext()))
        databind.rvWordList.layoutManager = LinearLayoutManager(requireContext())
        databind.rvWordList.adapter = adapter
        databind.rvWordList.isNestedScrollingEnabled = false
        databind.rvWordList.addItemDecoration(spacing)

        viewModel.loadSession(
            sessionTypeValue = args.sessionType,
            sessionWordCount = args.sessionWordCount,
            answeredCount = args.answeredCount,
            correctCount = args.correctCount,
            wrongCount = args.wrongCount,
            studyDurationMs = args.studyDurationMs,
            wordIds = args.words?.toList() ?: emptyList()
        )
    }

    override fun createObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        databind.tvDoneTitle.text = state.title
                        databind.tvDoneSubtitle.text = state.subtitle
                        databind.tvDoneTag.text = state.tagText
                        databind.tvMetricCompletedValue.text = state.completedCountText
                        databind.tvMetricDurationValue.text = state.durationText
                        databind.tvMetricAccuracyValue.text = state.accuracyText
                        databind.tvMetricQualityValue.text = state.qualityText
                        databind.tvMetricAnsweredValue.text = state.answeredText
                        databind.tvMetricWrongValue.text = state.wrongText
                        databind.tvMetricEfficiencyValue.text = state.efficiencyText
                    }
                }
                launch {
                    viewModel.wordRows.collect { rows ->
                        adapter.submitList(rows)
                    }
                }
            }
        }
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (val target = event.target) {
            is LearningDoneViewModel.Route.ToLearning -> {
                startActivity(learningEntry.createLearningIntent(requireContext(), target.request))
                if (target.replaceCurrent) {
                    requireActivity().finish()
                }
            }

            LearningDoneViewModel.Route.ToCheckIn -> {
                findNavController().navigate(R.id.action_learningDoneFragment_to_learningCheckInFragment)
            }
        }
    }
}
