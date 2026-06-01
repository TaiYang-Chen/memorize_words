package com.chen.memorizewords.feature.learning.ui.practice

import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.core.ui.fragment.BaseVmDbFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.practice.PracticeMode
import com.chen.memorizewords.feature.learning.PracticeActivity
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.FeatureLearningFragmentPracticeListeningBinding
import com.chen.memorizewords.feature.learning.shouldUseListeningCustomHeader
import com.chen.memorizewords.feature.learning.ui.practice.listening.audio.ListeningAudioPlayer
import com.chen.memorizewords.feature.learning.ui.practice.listening.renderer.ListeningPracticeRenderer
import com.chen.memorizewords.domain.practice.speech.SpeechAudioOutput
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ListeningPracticeFragment :
    BaseVmDbFragment<ListeningPracticeViewModel, FeatureLearningFragmentPracticeListeningBinding>() {

    override val viewModel: ListeningPracticeViewModel by viewModels()
    private val sessionViewModel: PracticeSessionViewModel by activityViewModels()

    private val audioPlayer = ListeningAudioPlayer()
    private lateinit var renderer: ListeningPracticeRenderer
    private var practiceMode: PracticeMode = PracticeMode.LISTENING

    override fun setLayout(): Int = R.layout.feature_learning_fragment_practice_listening

    override fun initView(savedInstanceState: Bundle?) {
        val selectedIds = arguments?.getLongArray(PracticeActivity.ARG_SELECTED_WORD_IDS)
        val randomCount = arguments?.getInt(PracticeActivity.ARG_RANDOM_COUNT, 20) ?: 20
        practiceMode = arguments?.getString(PracticeActivity.ARG_PRACTICE_MODE)
            ?.let { runCatching { PracticeMode.valueOf(it) }.getOrNull() }
            ?: PracticeMode.LISTENING

        viewModel.loadWithSelection(selectedIds, randomCount)
        databind.layoutHeaderRoot.isVisible = shouldUseListeningCustomHeader(practiceMode)
        renderer = createRenderer()
        bindActions()
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleExitRequest()
                }
            }
        )
        if (!viewModel.uiState.value.hasStarted) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.startSavedSession()
                if (databind.layoutHeaderRoot.isVisible && viewModel.consumeModeSwitchHint()) {
                    viewModel.showModeSwitchHintDialog()
                }
            }
        }
    }

    override fun createObserver() {
        observeUi()
    }

    override fun onConfirmDialog(event: UiEvent.Dialog.Confirm) {
        if (event.action == ListeningPracticeViewModel.ACTION_EXIT_LISTENING_PRACTICE) {
            requireActivity().finish()
            return
        }
        super.onConfirmDialog(event)
    }

    override fun onDestroyView() {
        audioPlayer.release()
        super.onDestroyView()
    }

    private fun createRenderer(): ListeningPracticeRenderer {
        return ListeningPracticeRenderer(
            binding = databind,
            callbacks = object : ListeningPracticeRenderer.Callbacks {
                override fun onSpellingLetterSelected(letterId: Long) {
                    viewModel.onSpellingLetterSelected(letterId)
                }

                override fun onSpellingDeleteLast() {
                    viewModel.onSpellingDeleteLast()
                }

                override fun onStudyExampleAudioRequested(index: Int) {
                    playStudyExampleAudio(index)
                }

                override fun onReportWordAudioRequested(row: ListeningReportWordUi) {
                    playReportWordAudio(row)
                }

                override fun onAutoPlayRequested() {
                    playCurrentAudio(showMissingToast = false)
                }
            }
        )
    }

    private fun bindActions() {
        databind.btnBack.setOnClickListener { handleExitRequest() }
        databind.btnSettings.setOnClickListener { showModeDialog() }
        databind.btnPlayAudio.setOnClickListener { playCurrentAudio() }
        databind.tvStudyPhoneticLabel.setOnClickListener { viewModel.onStudyPhoneticToggle() }
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
    }

    private fun observeUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderer.render(state)
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

    private fun playReportWordAudio(
        row: ListeningReportWordUi,
        showMissingToast: Boolean = true
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val resolved = viewModel.ensureReportWordSpeech(row.wordId, row.speechLocale)?.audioOutput
            if (resolved == null) {
                showMissingAudioToast(showMissingToast)
                return@launch
            }
            playResolvedAudio(resolved)
        }
    }

    private fun playCurrentAudio(showMissingToast: Boolean = true) {
        val output = viewModel.uiState.value.speech?.audioOutput
        if (output == null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val resolved = viewModel.ensureCurrentSpeech()?.audioOutput
                if (resolved == null) {
                    showMissingAudioToast(showMissingToast)
                    return@launch
                }
                playResolvedAudio(resolved)
            }
            return
        }
        playResolvedAudio(output)
    }

    private fun playStudyExampleAudio(index: Int, showMissingToast: Boolean = true) {
        viewLifecycleOwner.lifecycleScope.launch {
            val resolved = viewModel.ensureStudyExampleSpeech(index)?.audioOutput
            if (resolved == null) {
                showMissingAudioToast(showMissingToast)
                return@launch
            }
            playResolvedAudio(resolved)
        }
    }

    private fun playResolvedAudio(output: SpeechAudioOutput) {
        audioPlayer.play(
            output = output,
            onUnavailable = { showMissingAudioToast(show = true) }
        )
    }

    private fun showMissingAudioToast(show: Boolean) {
        if (!show) return
        val safeContext = context ?: return
        Toast.makeText(
            safeContext,
            R.string.practice_audio_unavailable,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showModeDialog() {
        ListeningModeDialogFragment.show(this)
    }

    private fun handleExitRequest() {
        viewModel.requestExitPracticeConfirm()
    }
}
