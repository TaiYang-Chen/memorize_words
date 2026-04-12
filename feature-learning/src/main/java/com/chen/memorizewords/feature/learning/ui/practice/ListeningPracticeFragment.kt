package com.chen.memorizewords.feature.learning.ui.practice

import android.content.res.ColorStateList
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.core.ui.fragment.BaseVmDbFragment
import com.chen.memorizewords.feature.learning.PracticeActivity
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.FragmentPracticeListeningBinding
import com.chen.memorizewords.feature.learning.ui.speech.prepareSpeechOutputAsync
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ListeningPracticeFragment :
    BaseVmDbFragment<ListeningPracticeViewModel, FragmentPracticeListeningBinding>() {

    override val viewModel: ListeningPracticeViewModel by viewModels()
    private val sessionViewModel: PracticeSessionViewModel by activityViewModels()

    private var mediaPlayer: MediaPlayer? = null
    private var isUpdatingSpellingInput: Boolean = false
    private var lastAutoPlayRequestId: Int = -1
    private var modeDialogShownOnce: Boolean = false

    private val spellingWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable?) {
            if (!isUpdatingSpellingInput) {
                viewModel.onSpellingInputChanged(s?.toString().orEmpty())
            }
        }
    }

    override fun setLayout(): Int = R.layout.fragment_practice_listening

    override fun initView(savedInstanceState: Bundle?) {
        val selectedIds = arguments?.getLongArray(PracticeActivity.ARG_SELECTED_WORD_IDS)
        val randomCount = arguments?.getInt(PracticeActivity.ARG_RANDOM_COUNT, 20) ?: 20
        viewModel.loadWithSelection(selectedIds, randomCount)
        bindActions()
        if (savedInstanceState == null) {
            databind.root.post {
                if (!modeDialogShownOnce && !viewModel.uiState.value.hasStarted) {
                    modeDialogShownOnce = true
                    showModeDialog(forceSelection = true)
                }
            }
        }
    }

    override fun createObserver() {
        observeUi()
    }

    override fun onDestroyView() {
        releaseMediaPlayer()
        databind.etSpellingAnswer.removeTextChangedListener(spellingWatcher)
        super.onDestroyView()
    }

    private fun bindActions() {
        databind.btnBack.setOnClickListener { requireActivity().finish() }
        databind.btnSettings.setOnClickListener { showModeDialog(forceSelection = false) }
        databind.btnPlayAudio.setOnClickListener { playCurrentAudio() }
        databind.btnPlayStudyAudio.setOnClickListener { playCurrentAudio() }
        databind.btnOption1.setOnClickListener { viewModel.onMeaningOptionSelected(0) }
        databind.btnOption2.setOnClickListener { viewModel.onMeaningOptionSelected(1) }
        databind.btnOption3.setOnClickListener { viewModel.onMeaningOptionSelected(2) }
        databind.btnOption4.setOnClickListener { viewModel.onMeaningOptionSelected(3) }
        databind.btnRevealAnswer.setOnClickListener { viewModel.onRevealAnswer() }
        databind.btnSkip.setOnClickListener { viewModel.onSkipQuestion() }
        databind.btnSubmitSpelling.setOnClickListener { viewModel.submitSpellingAnswer() }
        databind.btnPrimaryAction.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.showStudyState) {
                viewModel.onContinueAfterStudy()
            } else if (state.showReportState) {
                requireActivity().finish()
            }
        }
        databind.etSpellingAnswer.addTextChangedListener(spellingWatcher)
        databind.etSpellingAnswer.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                viewModel.submitSpellingAnswer()
                true
            } else {
                false
            }
        }
    }

    private fun observeUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderState(state)
                        sessionViewModel.updateSessionSummary(state.summary)
                    }
                }
                launch {
                    viewModel.sessionWordIds.collect { ids ->
                        if (ids.isNotEmpty()) {
                            sessionViewModel.setSessionWordIds(ids)
                        }
                    }
                }
            }
        }
    }

    private fun renderState(state: ListeningPracticeViewModel.ListeningUiState) {
        databind.tvProgress.text = state.headerProgressText
        databind.progressSession.max = state.progressMax.coerceAtLeast(1)
        databind.progressSession.progress = state.progressValue.coerceAtLeast(0)
        databind.tvModeBadge.text = state.reviewIndicatorText.ifBlank { state.modeTitle }
        databind.tvPhonetic.text = state.phoneticChipText
        databind.tvPrompt.text = state.promptText
        databind.tvPromptHint.isVisible = state.promptHint.isNotBlank() && !state.showReportState
        databind.tvPromptHint.text = state.promptHint

        databind.layoutPracticeRoot.isVisible = !state.showStudyState && !state.showReportState
        databind.layoutStudyRoot.isVisible = state.showStudyState
        databind.layoutReportRoot.isVisible = state.showReportState
        databind.layoutMeaningOptions.isVisible = state.showMeaningQuestion
        databind.layoutSpellingQuestion.isVisible = state.showSpellingQuestion
        databind.layoutPracticeActions.isVisible =
            state.bottomActionVisible && !state.showReportState

        databind.tvFeedback.isVisible = state.feedbackMessage.isNotBlank()
        databind.tvFeedback.text = state.feedbackMessage
        renderFeedback(state.feedbackTone)

        databind.btnPlayAudio.isEnabled = !state.loading
        databind.btnPlayStudyAudio.isEnabled = !state.loading

        renderOptionButton(
            databind.btnOption1,
            state.meaningOptions.getOrNull(0),
            state.selectedMeaningIndex == 0
        )
        renderOptionButton(
            databind.btnOption2,
            state.meaningOptions.getOrNull(1),
            state.selectedMeaningIndex == 1
        )
        renderOptionButton(
            databind.btnOption3,
            state.meaningOptions.getOrNull(2),
            state.selectedMeaningIndex == 2
        )
        renderOptionButton(
            databind.btnOption4,
            state.meaningOptions.getOrNull(3),
            state.selectedMeaningIndex == 3
        )

        syncSpellingInput(state.spellingInput)
        databind.btnSubmitSpelling.isEnabled = state.spellingSubmitEnabled

        databind.tvStudyWord.text = state.studyWord
        databind.tvStudyPhonetic.text = state.studyPhoneticChipText
        databind.tvStudyStatus.text = state.studyReviewStatusText
        databind.tvStudyExampleEn.text = state.studyExampleEnglish
        databind.tvStudyExampleEn.isVisible = state.studyExampleEnglish.isNotBlank()
        databind.tvStudyExampleZh.text = state.studyExampleChinese
        databind.tvStudyExampleZh.isVisible = state.studyExampleChinese.isNotBlank()
        databind.tvStudyExampleLabel.isVisible =
            state.studyExampleEnglish.isNotBlank() || state.studyExampleChinese.isNotBlank()
        renderStudyDefinitions(state.studyDefinitions)

        databind.tvReportTitle.text = state.promptText
        databind.tvReportHint.text = state.promptHint
        databind.tvReportAccuracy.text = state.report.accuracyText
        databind.tvReportSummary.text = state.report.summaryText
        databind.tvReportReviewedCount.text = state.report.reviewedCountText
        databind.tvReportSkippedCount.text = state.report.skippedCountText
        renderReportRows(
            databind.layoutReportReviewedWords,
            state.report.reviewedWords,
            emptyTextRes = R.string.practice_listening_report_metric_none
        )
        renderReportRows(
            databind.layoutReportUnfinishedWords,
            state.report.unfinishedWords,
            emptyTextRes = R.string.practice_listening_report_metric_none
        )

        databind.btnPrimaryAction.isVisible = state.primaryButtonText.isNotBlank()
        databind.btnPrimaryAction.text = state.primaryButtonText
        databind.btnPrimaryAction.isEnabled = state.primaryButtonEnabled

        if (state.autoPlayRequestId > 0 && state.autoPlayRequestId != lastAutoPlayRequestId) {
            lastAutoPlayRequestId = state.autoPlayRequestId
            playCurrentAudio(showMissingToast = false)
        }
    }

    private fun renderFeedback(tone: ListeningFeedbackTone) {
        val (textColor, backgroundColor) = when (tone) {
            ListeningFeedbackTone.SUCCESS -> "#0F766E" to "#D1FAE5"
            ListeningFeedbackTone.ERROR -> "#B91C1C" to "#FEE2E2"
            ListeningFeedbackTone.INFO -> "#0F4C81" to "#DBEAFE"
            ListeningFeedbackTone.NONE -> "#5B7286" to "#F1F5F9"
        }
        databind.tvFeedback.setTextColor(Color.parseColor(textColor))
        databind.tvFeedback.setBackgroundResource(R.drawable.module_learning_bg_listening_chip)
        databind.tvFeedback.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor(backgroundColor))
    }

    private fun renderOptionButton(
        button: MaterialButton,
        option: ListeningMeaningOptionUi?,
        isSelected: Boolean
    ) {
        if (option == null) {
            button.isVisible = false
            return
        }
        button.isVisible = true
        button.text = "${option.partOfSpeech}\n${option.content}"
        button.isEnabled = !viewModel.uiState.value.loading
        if (isSelected) {
            button.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#0EA5A4"))
            button.strokeColor = ColorStateList.valueOf(Color.parseColor("#0F766E"))
            button.setTextColor(Color.WHITE)
        } else {
            button.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            button.strokeColor = ColorStateList.valueOf(Color.parseColor("#D9E2EC"))
            button.setTextColor(Color.parseColor("#1F2937"))
        }
    }

    private fun syncSpellingInput(answer: String) {
        if (databind.etSpellingAnswer.text?.toString() == answer) return
        isUpdatingSpellingInput = true
        databind.etSpellingAnswer.setText(answer)
        databind.etSpellingAnswer.setSelection(databind.etSpellingAnswer.text?.length ?: 0)
        isUpdatingSpellingInput = false
    }

    private fun renderStudyDefinitions(definitions: List<ListeningStudyDefinitionUi>) {
        val container = databind.layoutStudyDefinitions
        container.removeAllViews()
        definitions.forEach { item ->
            container.addView(
                buildRowView(
                    title = item.partOfSpeech,
                    value = item.meaning,
                    completed = true
                )
            )
        }
    }

    private fun renderReportRows(
        container: LinearLayout,
        rows: List<ListeningReportWordUi>,
        emptyTextRes: Int
    ) {
        container.removeAllViews()
        if (rows.isEmpty()) {
            container.addView(
                TextView(requireContext()).apply {
                    text = getString(emptyTextRes)
                    textSize = 14f
                    setTextColor(Color.parseColor("#94A3B8"))
                }
            )
            return
        }
        rows.forEach { row ->
            container.addView(buildRowView(row.word, row.progressText, row.isCompleted))
        }
    }

    private fun buildRowView(
        title: String,
        value: String,
        completed: Boolean
    ): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            background = resources.getDrawable(R.drawable.module_learning_bg_listening_row, null)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = dp(10)
            layoutParams = params

            addView(
                TextView(context).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    text = title
                    textSize = 15f
                    setTextColor(Color.parseColor("#111827"))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
            )
            addView(
                TextView(context).apply {
                    text = value
                    textSize = 13f
                    setTextColor(Color.parseColor(if (completed) "#0F766E" else "#B45309"))
                    background =
                        resources.getDrawable(R.drawable.module_learning_bg_listening_chip, null)
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                }
            )
        }
    }

    private fun showModeDialog(forceSelection: Boolean) {
        val modes = ListeningPracticeMode.entries.toList()
        var selectedIndex = modes.indexOf(viewModel.uiState.value.mode).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.practice_listening_settings_title)
            .setMessage(
                getString(
                    if (forceSelection) {
                        R.string.practice_listening_select_mode_hint
                    } else {
                        R.string.practice_listening_settings_message
                    }
                )
            )
            .setSingleChoiceItems(
                modes.map { mode ->
                    "${getString(mode.titleRes)}\n${getString(mode.descriptionRes)}"
                }.toTypedArray(),
                selectedIndex
            ) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(
                if (forceSelection) {
                    R.string.practice_listening_settings_start
                } else {
                    R.string.practice_listening_settings_confirm
                }
            ) { _, _ ->
                viewModel.startSession(modes[selectedIndex])
            }
            .apply {
                if (!forceSelection) {
                    setNegativeButton(R.string.practice_listening_settings_cancel, null)
                }
            }
            .setCancelable(!forceSelection)
            .show()
    }

    private fun playCurrentAudio(showMissingToast: Boolean = true) {
        val output = viewModel.uiState.value.speech?.audioOutput
        if (output == null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val resolved = viewModel.ensureCurrentSpeech()?.audioOutput
                if (resolved == null) {
                    if (showMissingToast && context != null) {
                        Toast.makeText(
                            requireContext(),
                            R.string.practice_audio_unavailable,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }
                playResolvedAudio(resolved)
            }
            return
        }
        playResolvedAudio(output)
    }

    private fun playResolvedAudio(output: com.chen.memorizewords.speech.api.SpeechAudioOutput) {
        releaseMediaPlayer()
        val player = MediaPlayer()
        val prepared = player.prepareSpeechOutputAsync(
            output = output,
            onPrepared = {
                it.setOnCompletionListener { _ -> releaseMediaPlayer() }
                it.start()
            },
            onError = {
                releaseMediaPlayer()
                context?.let { safeContext ->
                    Toast.makeText(
                        safeContext,
                        R.string.practice_audio_unavailable,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
        if (prepared) {
            mediaPlayer = player
        }
    }

    private fun releaseMediaPlayer() {
        val player = mediaPlayer ?: return
        mediaPlayer = null
        runCatching {
            runCatching { player.stop() }
            player.release()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
