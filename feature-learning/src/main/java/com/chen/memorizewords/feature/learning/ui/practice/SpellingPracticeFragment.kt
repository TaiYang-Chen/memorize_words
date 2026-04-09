package com.chen.memorizewords.feature.learning.ui.practice

import android.content.res.ColorStateList
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.feature.learning.PracticeActivity
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.FragmentPracticeSpellingBinding
import com.chen.memorizewords.speech.api.SpeechAudioOutput
import com.chen.memorizewords.feature.learning.ui.speech.setSpeechDataSource
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SpellingPracticeFragment : Fragment() {

    private var _binding: FragmentPracticeSpellingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SpellingPracticeViewModel by viewModels()
    private val sessionViewModel: PracticeSessionViewModel by activityViewModels()

    private var mediaPlayer: MediaPlayer? = null
    private var isUpdatingAnswerText: Boolean = false
    private var lastAutoPlayRequestId: Int = -1
    private val slotViews = mutableListOf<TextView>()
    private val letterButtons = mutableListOf<MaterialButton>()
    private var deleteButton: MaterialButton? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPracticeSpellingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        releaseMediaPlayer()
        slotViews.clear()
        letterButtons.clear()
        deleteButton = null
        _binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnSubmit.setOnClickListener { viewModel.onSubmit() }
        binding.btnNextWord.setOnClickListener { viewModel.nextWord() }
        binding.btnHint.setOnClickListener { viewModel.onHint() }
        binding.btnClearHandwriting.setOnClickListener { binding.handwritingView.clearCanvas() }
        binding.btnPlayAudio.setOnClickListener { playAudio(showMissingAudioToast = true) }
        binding.etAnswer.doAfterTextChangedCompat { text ->
            if (!isUpdatingAnswerText) {
                viewModel.onKeyboardInputChanged(text)
            }
        }
        observeUi()
        val selectedIds = arguments?.getLongArray(PracticeActivity.ARG_SELECTED_WORD_IDS)
        val randomCount = arguments?.getInt(PracticeActivity.ARG_RANDOM_COUNT, 20) ?: 20
        viewModel.loadWithSelection(selectedIds, randomCount)
    }

    private fun playAudio(showMissingAudioToast: Boolean) {
        val output = viewModel.uiState.value.speech?.audioOutput
        if (output == null) {
            if (showMissingAudioToast) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val retriedOutput = viewModel.ensureCurrentSpeech()?.audioOutput
                        ?: viewModel.uiState.value.speech?.audioOutput
                    if (retriedOutput != null && context != null && _binding != null) {
                        playResolvedAudio(retriedOutput, showMissingAudioToast = true)
                    } else {
                        context?.let {
                            Toast.makeText(
                                it,
                                R.string.practice_audio_unavailable,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            return
        }
        playResolvedAudio(output, showMissingAudioToast)
    }

    private fun playResolvedAudio(
        output: SpeechAudioOutput,
        showMissingAudioToast: Boolean
    ) {
        val safeContext = context ?: return
        releaseMediaPlayer()
        val player = MediaPlayer()
        val prepared = runCatching {
            player.setSpeechDataSource(output)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { releaseMediaPlayer() }
            player.setOnErrorListener { _, _, _ ->
                releaseMediaPlayer()
                true
            }
            player.prepareAsync()
            true
        }.getOrElse {
            runCatching { player.release() }
            if (showMissingAudioToast) {
                Toast.makeText(
                    safeContext,
                    R.string.practice_audio_unavailable,
                    Toast.LENGTH_SHORT
                ).show()
            }
            false
        }
        if (prepared) mediaPlayer = player
    }

    private fun releaseMediaPlayer() {
        val player = mediaPlayer ?: return
        mediaPlayer = null
        runCatching {
            runCatching { player.stop() }
            player.release()
        }
    }

    private fun observeUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.tvMeaning.text = state.meaning
                        binding.tvWordLength.text = state.wordLengthHint
                        binding.tvProgress.text = state.progressText
                        binding.progressPractice.max = state.progressMax.coerceAtLeast(1)
                        binding.progressPractice.progress = state.progressValue.coerceAtLeast(0)
                        binding.tvResult.isVisible = state.feedback.isNotBlank()
                        binding.tvResult.text = state.feedback
                        binding.layoutSummary.isVisible = state.isCompleted && state.summaryText.isNotBlank()
                        binding.tvSummary.text = state.summaryText
                        binding.btnSubmit.isEnabled = state.canSubmit
                        binding.btnNextWord.isEnabled = state.canNext
                        binding.btnHint.isEnabled = state.canHint
                        binding.etAnswer.isEnabled = state.canEditAnswer
                        binding.inputAnswerLayout.isEnabled = state.canEditAnswer
                        binding.btnNextWord.text = getString(
                            if (state.isCompleted) {
                                R.string.practice_completed
                            } else {
                                R.string.practice_next_word
                            }
                        )
                        binding.tvAttemptStatus.text = getString(
                            R.string.practice_spelling_attempt_status,
                            state.attemptCount,
                            state.hintCount
                        )
                        syncAnswerInput(state.currentAnswer)
                        renderSlots(state.answerSlots)
                        renderLetters(state.letters)
                        sessionViewModel.updateSessionSummary(state.summary)
                        if (!state.isCompleted &&
                            state.autoPlayRequestId > 0 &&
                            state.autoPlayRequestId != lastAutoPlayRequestId
                        ) {
                            lastAutoPlayRequestId = state.autoPlayRequestId
                            playAudio(showMissingAudioToast = false)
                        }
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

    private fun syncAnswerInput(answer: String) {
        if (binding.etAnswer.text?.toString() == answer) return
        isUpdatingAnswerText = true
        binding.etAnswer.setText(answer)
        binding.etAnswer.setSelection(binding.etAnswer.text?.length ?: 0)
        isUpdatingAnswerText = false
    }

    private fun renderSlots(slots: List<SpellingPracticeViewModel.AnswerSlot>) {
        val container = binding.layoutSlots
        if (slots.isEmpty()) {
            container.removeAllViews()
            slotViews.clear()
            return
        }
        val slotWidth = dp(28)
        val slotHeight = dp(36)
        val margin = dp(6)
        if (slotViews.size != slots.size) {
            container.removeAllViews()
            slotViews.clear()
            repeat(slots.size) {
                val textView = TextView(requireContext()).apply {
                    textSize = 18f
                    gravity = Gravity.CENTER
                }
                val lp = LinearLayout.LayoutParams(slotWidth, slotHeight).apply {
                    setMargins(margin, 0, margin, 0)
                }
                container.addView(textView, lp)
                slotViews += textView
            }
        }
        slots.forEachIndexed { index, slot ->
            val view = slotViews[index]
            view.text = slot.letter
            view.setTextColor(
                Color.parseColor(if (slot.isHintLocked) "#1D4ED8" else "#0F172A")
            )
            view.background = ContextCompat.getDrawable(
                requireContext(),
                if (slot.isHintLocked) R.drawable.module_learning_bg_practice_handwriting
                else R.drawable.module_learning_bg_practice_slot
            )
        }
    }

    private fun renderLetters(letters: List<SpellingPracticeViewModel.LetterItem>) {
        val needsRebuild = letterButtons.size != letters.size ||
            letterButtons.zip(letters).any { (button, item) ->
                button.text?.toString() != item.letter.toString()
            }
        if (needsRebuild) {
            binding.gridLetters.removeAllViews()
            letterButtons.clear()
            letters.forEach { item ->
                val button = createLetterButton(item.letter.toString())
                binding.gridLetters.addView(button, createGridLayoutParams())
                letterButtons += button
            }
            deleteButton = createLetterButton("").also { button ->
                button.icon = ContextCompat.getDrawable(requireContext(), R.drawable.module_learning_clear)
                button.iconTint = ColorStateList.valueOf(Color.parseColor("#64748B"))
                button.iconPadding = 0
                binding.gridLetters.addView(button, createGridLayoutParams())
            }
        }
        letters.forEachIndexed { index, item ->
            val button = letterButtons[index]
            button.text = item.letter.toString()
            button.isEnabled = item.enabled
            button.alpha = if (item.enabled) 1f else 0.35f
            button.setOnClickListener { viewModel.onLetterClick(item.id) }
        }
        deleteButton?.setOnClickListener { viewModel.onDelete() }
    }

    private fun createLetterButton(text: String): MaterialButton {
        return MaterialButton(
            requireContext(),
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            this.text = text
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.parseColor("#0F172A"))
            cornerRadius = dp(12)
            strokeWidth = dp(1)
            strokeColor = ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F8FAFC"))
            insetTop = 0
            insetBottom = 0
        }
    }

    private fun createGridLayoutParams(): GridLayout.LayoutParams {
        return GridLayout.LayoutParams().apply {
            width = 0
            height = dp(48)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(dp(6), dp(6), dp(6), dp(6))
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}

private fun android.widget.EditText.doAfterTextChangedCompat(
    action: (String) -> Unit
) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: android.text.Editable?) {
            action(s?.toString().orEmpty())
        }
    })
}
