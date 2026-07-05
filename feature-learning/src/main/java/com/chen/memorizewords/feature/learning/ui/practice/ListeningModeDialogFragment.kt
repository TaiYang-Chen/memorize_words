package com.chen.memorizewords.feature.learning.ui.practice

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.ext.dpToPx
import com.chen.memorizewords.domain.practice.ListeningAnswerAreaPosition
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.DialogListeningModePickerBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.math.roundToInt

class ListeningModeDialogFragment : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "listening_mode_dialog"

        fun show(host: Fragment) {
            val fragmentManager = host.childFragmentManager
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            ListeningModeDialogFragment().show(fragmentManager, TAG)
        }
    }

    private var _binding: DialogListeningModePickerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ListeningPracticeViewModel by lazy {
        ViewModelProvider(requireParentFragment())[ListeningPracticeViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogListeningModePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindContent()
        bindActions()
        renderSelection(viewModel.uiState.value.mode)
        renderAnswerAreaPosition(viewModel.uiState.value.answerAreaPosition)
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        bottomSheet.setBackgroundResource(android.R.color.transparent)
        BottomSheetBehavior.from(bottomSheet).apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun bindContent() {
        binding.tvMeaningTitle.text = listeningModeDisplayName(ListeningPracticeMode.MEANING)
        binding.tvMeaningDescription.text =
            getString(ListeningPracticeMode.MEANING.descriptionRes)
        binding.tvSpellingTitle.text = listeningModeDisplayName(ListeningPracticeMode.SPELLING)
        binding.tvSpellingDescription.text =
            getString(ListeningPracticeMode.SPELLING.descriptionRes)
    }

    private fun bindActions() {
        binding.cardMeaning.setOnClickListener { onModeSelected(ListeningPracticeMode.MEANING) }
        binding.cardSpelling.setOnClickListener { onModeSelected(ListeningPracticeMode.SPELLING) }
        binding.btnAnswerPositionTop.setOnClickListener {
            onAnswerAreaPositionSelected(ListeningAnswerAreaPosition.TOP)
        }
        binding.btnAnswerPositionMiddle.setOnClickListener {
            onAnswerAreaPositionSelected(ListeningAnswerAreaPosition.MIDDLE)
        }
        binding.btnAnswerPositionBottom.setOnClickListener {
            onAnswerAreaPositionSelected(ListeningAnswerAreaPosition.BOTTOM)
        }
    }

    private fun onModeSelected(mode: ListeningPracticeMode) {
        val currentMode = viewModel.uiState.value.mode
        if (mode != currentMode) {
            viewModel.onModeChanged(mode)
        }
        dismiss()
    }

    private fun onAnswerAreaPositionSelected(position: ListeningAnswerAreaPosition) {
        viewModel.onAnswerAreaPositionChanged(position)
        renderAnswerAreaPosition(position)
    }

    private fun renderSelection(selectedMode: ListeningPracticeMode) {
        renderCard(
            container = binding.cardMeaning,
            titleView = binding.tvMeaningTitle,
            descriptionView = binding.tvMeaningDescription,
            selected = selectedMode == ListeningPracticeMode.MEANING
        )
        renderCard(
            container = binding.cardSpelling,
            titleView = binding.tvSpellingTitle,
            descriptionView = binding.tvSpellingDescription,
            selected = selectedMode == ListeningPracticeMode.SPELLING
        )
    }

    private fun renderAnswerAreaPosition(selectedPosition: ListeningAnswerAreaPosition) {
        renderPositionButton(
            button = binding.btnAnswerPositionTop,
            selected = selectedPosition == ListeningAnswerAreaPosition.TOP
        )
        renderPositionButton(
            button = binding.btnAnswerPositionMiddle,
            selected = selectedPosition == ListeningAnswerAreaPosition.MIDDLE
        )
        renderPositionButton(
            button = binding.btnAnswerPositionBottom,
            selected = selectedPosition == ListeningAnswerAreaPosition.BOTTOM
        )
    }

    private fun renderPositionButton(
        button: TextView,
        selected: Boolean
    ) {
        button.setBackgroundResource(
            if (selected) {
                R.drawable.module_learning_bg_listening_mode_option_selected
            } else {
                R.drawable.module_learning_bg_listening_mode_option_normal
            }
        )
        button.setTextColor(
            if (selected) Color.parseColor("#111827") else Color.parseColor("#6B7280")
        )
    }

    private fun renderCard(
        container: LinearLayout,
        titleView: TextView,
        descriptionView: TextView,
        selected: Boolean
    ) {
        container.setBackgroundResource(
            if (selected) {
                R.drawable.module_learning_bg_listening_mode_option_selected
            } else {
                R.drawable.module_learning_bg_listening_mode_option_normal
            }
        )
        titleView.setTextColor(
            if (selected) Color.parseColor("#111827") else Color.parseColor("#1F2937")
        )
        descriptionView.setTextColor(
            if (selected) Color.parseColor("#4B5563") else Color.parseColor("#6B7280")
        )
    }

}
