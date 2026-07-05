package com.chen.memorizewords.feature.learning.ui.practice

import android.content.res.ColorStateList
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.core.ui.ext.dpToPx
import com.chen.memorizewords.core.ui.fragment.BaseVmDbFragment
import com.chen.memorizewords.domain.word.query.WordDetail
import com.chen.memorizewords.domain.word.query.WordReadFacade
import com.chen.memorizewords.feature.learning.PracticeActivity
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.FragmentPracticeSpellingBinding
import com.chen.memorizewords.feature.learning.ui.speech.setSpeechDataSource
import com.chen.memorizewords.domain.practice.speech.SpeechAudioOutput
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class SpellingPracticeFragment :
    BaseVmDbFragment<SpellingPracticeViewModel, FragmentPracticeSpellingBinding>() {

    @Inject
    lateinit var wordReadFacade: WordReadFacade

    override val viewModel: SpellingPracticeViewModel by viewModels()
    private val sessionViewModel: PracticeSessionViewModel by activityViewModels()

    private var mediaPlayer: MediaPlayer? = null
    private var isUpdatingAnswerText: Boolean = false
    private var lastAutoPlayRequestId: Int = -1
    private val slotViews = mutableListOf<TextView>()
    private val letterButtons = mutableListOf<MaterialButton>()
    private var deleteButton: MaterialButton? = null
    private var isHandwritingExpanded: Boolean = false
    private var drawerStartHeight: Int = 0
    private var drawerStartY: Float = 0f
    private var drawerWasDragged: Boolean = false
    private var lastWrongShakeRequestId: Int = 0
    private var lastAutoOpenDetailRequestId: Int = 0
    private var autoOpenDetailJob: Job? = null
    private var detailLoadJob: Job? = null
    private var isPracticeDetailVisible: Boolean = false
    private var practiceDetailWordId: Long = -1L
    private var completionNavigated: Boolean = false

    override fun setLayout(): Int = R.layout.fragment_practice_spelling

    override fun initView(savedInstanceState: Bundle?) {
        databind.btnBack.setOnClickListener { requireActivity().finish() }
        databind.btnSubmit.setOnClickListener { viewModel.onSubmit() }
        databind.btnNextWord.setOnClickListener { viewModel.nextWord() }
        databind.btnHint.setOnClickListener { viewModel.onHint() }
        databind.btnClearHandwriting.setOnClickListener { databind.handwritingView.clearCanvas() }
        databind.btnResetHandwriting.setOnClickListener { databind.handwritingView.resetViewport() }
        databind.btnPlayAudio.setOnClickListener { playAudio(showMissingAudioToast = true) }
        databind.btnDetailBack.setOnClickListener { hidePracticeDetail() }
        databind.btnDetailSpeaker.setOnClickListener { playAudio(showMissingAudioToast = true) }
        databind.btnDetailNextWord.setOnClickListener {
            if (viewModel.nextWord()) {
                hidePracticeDetail()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isPracticeDetailVisible) {
                        hidePracticeDetail()
                    } else {
                        requireActivity().finish()
                    }
                }
            }
        )
        databind.layoutHandwritingHandle.setOnTouchListener(::onHandwritingHandleTouch)
        databind.handwritingContainer.isVisible = false
        databind.etAnswer.doAfterTextChangedCompat { text ->
            if (!isUpdatingAnswerText) {
                viewModel.onKeyboardInputChanged(text)
            }
        }
        val selectedIds = arguments?.getLongArray(PracticeActivity.ARG_SELECTED_WORD_IDS)
        val randomCount = arguments?.getInt(PracticeActivity.ARG_RANDOM_COUNT, 20) ?: 20
        viewModel.loadWithSelection(selectedIds, randomCount)
    }

    override fun createObserver() {
        observeUi()
    }

    override fun onDestroyView() {
        releaseMediaPlayer()
        autoOpenDetailJob?.cancel()
        autoOpenDetailJob = null
        detailLoadJob?.cancel()
        detailLoadJob = null
        slotViews.clear()
        letterButtons.clear()
        deleteButton = null
        super.onDestroyView()
    }

    private fun playAudio(showMissingAudioToast: Boolean) {
        val output = viewModel.uiState.value.speech?.audioOutput
        if (output == null) {
            if (showMissingAudioToast) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val retriedOutput = viewModel.ensureCurrentSpeech()?.audioOutput
                        ?: viewModel.uiState.value.speech?.audioOutput
                    if (retriedOutput != null && context != null && view != null) {
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
                        databind.tvMeaning.text = state.meaning
                        databind.tvWordLength.text = state.wordLengthHint
                        databind.tvProgress.text = buildCompactStatusText(state)
                        databind.progressPractice.max = state.progressMax.coerceAtLeast(1)
                        databind.progressPractice.progress = state.progressValue.coerceAtLeast(0)
                        databind.tvResult.isVisible = state.feedback.isNotBlank()
                        databind.tvResult.text = state.feedback
                        databind.btnSubmit.isEnabled = state.canSubmit
                        databind.btnNextWord.isEnabled = state.canNext
                        renderActionEmphasis(state)
                        databind.btnHint.isEnabled = state.canHint
                        databind.etAnswer.isEnabled = state.canEditAnswer
                        databind.inputAnswerLayout.isEnabled = state.canEditAnswer
                        databind.btnNextWord.text = getString(R.string.practice_next_word)
                        renderPracticeDetailAction(state)
                        closeStalePracticeDetail(state)
                        databind.tvAttemptStatus.text = getString(
                            R.string.practice_spelling_attempt_status,
                            state.attemptCount,
                            state.hintCount
                        )
                        syncAnswerInput(state.currentAnswer)
                        renderSlots(state.answerSlots)
                        shakeWrongSlots(state)
                        scheduleAutoOpenWordDetail(state)
                        renderLetters(state.letters)
                        sessionViewModel.updateSessionSummary(state.summary)
                        navigateToCompletionIfReady(state)
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

    private fun scheduleAutoOpenWordDetail(state: SpellingPracticeViewModel.SpellingUiState) {
        if (state.resultState != SpellingResultState.REVEALED_WRONG ||
            state.currentWordId <= 0L ||
            state.autoOpenDetailRequestId <= 0 ||
            state.autoOpenDetailRequestId == lastAutoOpenDetailRequestId
        ) {
            return
        }
        lastAutoOpenDetailRequestId = state.autoOpenDetailRequestId
        autoOpenDetailJob?.cancel()
        autoOpenDetailJob = viewLifecycleOwner.lifecycleScope.launch {
            val requestId = state.autoOpenDetailRequestId
            val wordId = state.currentWordId
            delay(500L)
            val latest = viewModel.uiState.value
            if (latest.resultState == SpellingResultState.REVEALED_WRONG &&
                latest.autoOpenDetailRequestId == requestId &&
                latest.currentWordId == wordId &&
                isAdded
            ) {
                showPracticeDetail(wordId)
            }
        }
    }

    private fun showPracticeDetail(wordId: Long) {
        if (wordId <= 0L) return
        isPracticeDetailVisible = true
        practiceDetailWordId = wordId
        databind.layoutPracticeDetail.isVisible = true
        databind.scrollPracticeDetail.scrollTo(0, 0)
        databind.tvDetailWord.text = viewModel.uiState.value.currentWord
        databind.tvDetailPhonetic.text = ""
        databind.tvDetailLoading.isVisible = true
        databind.layoutDetailContent.isVisible = false
        renderPracticeDetailAction(viewModel.uiState.value)
        detailLoadJob?.cancel()
        detailLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val detail = withContext(Dispatchers.IO) {
                wordReadFacade.getWordDetailById(wordId)
            }
            if (!isAdded || !isPracticeDetailVisible) return@launch
            if (detail == null) {
                databind.tvDetailLoading.text = getString(R.string.word_detail_unavailable)
                return@launch
            }
            renderPracticeDetail(detail)
        }
    }

    private fun hidePracticeDetail() {
        if (!isPracticeDetailVisible) return
        isPracticeDetailVisible = false
        practiceDetailWordId = -1L
        databind.layoutPracticeDetail.isVisible = false
        detailLoadJob?.cancel()
        detailLoadJob = null
    }

    private fun renderPracticeDetailAction(state: SpellingPracticeViewModel.SpellingUiState) {
        val isLastWord = state.progressMax > 0 && state.progressValue >= state.progressMax
        databind.btnDetailNextWord.text = getString(
            if (isLastWord) {
                R.string.practice_completed
            } else {
                R.string.practice_next_word
            }
        )
        databind.btnDetailNextWord.isEnabled = state.canNext
        databind.btnDetailNextWord.alpha = if (state.canNext) 1f else 0.55f
    }

    private fun closeStalePracticeDetail(state: SpellingPracticeViewModel.SpellingUiState) {
        if (!isPracticeDetailVisible) return
        if (state.isCompleted || state.currentWordId != practiceDetailWordId) {
            hidePracticeDetail()
        }
    }

    private fun renderPracticeDetail(detail: WordDetail) {
        val word = detail.word
        databind.tvDetailLoading.isVisible = false
        databind.layoutDetailContent.isVisible = true
        databind.tvDetailWord.text = word.word
        databind.tvDetailPhonetic.text = word.phoneticUS.orEmpty().ifBlank {
            word.phoneticUK.orEmpty()
        }
        renderDetailDefinitions(detail)
        renderDetailExamples(detail)
    }

    private fun renderDetailDefinitions(detail: WordDetail) {
        databind.layoutDetailDefinitions.removeAllViews()
        val definitions = detail.definitions
        if (definitions.isEmpty()) {
            databind.layoutDetailDefinitions.addView(
                createDetailTextView(getString(R.string.learning_share_empty_definitions))
            )
            return
        }
        definitions.forEach { definition ->
            val text = "${definition.partOfSpeech.name.lowercase()}. ${definition.meaningChinese}"
            databind.layoutDetailDefinitions.addView(createDetailTextView(text))
        }
    }

    private fun renderDetailExamples(detail: WordDetail) {
        databind.layoutDetailExamples.removeAllViews()
        val examples = detail.examples.take(3)
        databind.tvDetailExamplesTitle.isVisible = examples.isNotEmpty()
        databind.layoutDetailExamples.isVisible = examples.isNotEmpty()
        examples.forEach { example ->
            val text = buildString {
                append(example.englishSentence)
                example.chineseTranslation?.takeIf { it.isNotBlank() }?.let { translation ->
                    append('\n')
                    append(translation)
                }
            }
            databind.layoutDetailExamples.addView(createDetailTextView(text))
        }
    }

    private fun createDetailTextView(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.parseColor("#334155"))
            setLineSpacing(2.dpToPx(requireContext()).toFloat(), 1f)
            setPadding(0, 4.dpToPx(requireContext()), 0, 8.dpToPx(requireContext()))
        }
    }

    private fun syncAnswerInput(answer: String) {
        if (databind.etAnswer.text?.toString() == answer) return
        isUpdatingAnswerText = true
        databind.etAnswer.setText(answer)
        databind.etAnswer.setSelection(databind.etAnswer.text?.length ?: 0)
        isUpdatingAnswerText = false
    }

    private fun renderSlots(slots: List<SpellingPracticeViewModel.AnswerSlot>) {
        val container = databind.layoutSlots
        if (slots.isEmpty()) {
            container.removeAllViews()
            slotViews.clear()
            return
        }
        val compactSlots = slots.size >= 10
        val margin = when {
            slots.size >= 13 -> 1.dpToPx(requireContext())
            compactSlots -> 2.dpToPx(requireContext())
            else -> 4.dpToPx(requireContext())
        }
        val availableWidth = (container.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels) -
            container.paddingStart -
            container.paddingEnd
        val slotWidth = ((availableWidth - margin * 2 * slots.size) / slots.size)
            .coerceIn(if (compactSlots) 12.dpToPx(requireContext()) else 22.dpToPx(requireContext()), if (compactSlots) 22.dpToPx(requireContext()) else 34.dpToPx(requireContext()))
        val slotHeight = if (compactSlots) 30.dpToPx(requireContext()) else 38.dpToPx(requireContext())
        if (slotViews.size != slots.size) {
            container.removeAllViews()
            slotViews.clear()
            repeat(slots.size) {
                val textView = TextView(requireContext()).apply {
                    textSize = when {
                        slotWidth < 16.dpToPx(requireContext()) -> 12f
                        compactSlots -> 14f
                        slotWidth < 26.dpToPx(requireContext()) -> 15f
                        else -> 18f
                    }
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
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
            view.layoutParams = (view.layoutParams as? LinearLayout.LayoutParams)?.apply {
                width = slotWidth
                height = slotHeight
                setMargins(margin, 0, margin, 0)
            } ?: LinearLayout.LayoutParams(slotWidth, slotHeight).apply {
                setMargins(margin, 0, margin, 0)
            }
            view.textSize = when {
                slotWidth < 16.dpToPx(requireContext()) -> 12f
                compactSlots -> 14f
                slotWidth < 26.dpToPx(requireContext()) -> 15f
                else -> 18f
            }
            view.text = slot.letter
            view.setTextColor(
                Color.parseColor(
                    when {
                        slot.isWrong -> "#DC2626"
                        slot.isHintLocked -> "#1D4ED8"
                        else -> "#0F172A"
                    }
                )
            )
            view.background = ContextCompat.getDrawable(
                requireContext(),
                if (compactSlots) {
                    when {
                        slot.isWrong -> R.drawable.module_learning_bg_spelling_slot_line_wrong
                        slot.isHintLocked -> R.drawable.module_learning_bg_spelling_slot_line_hint
                        else -> R.drawable.module_learning_bg_spelling_slot_line
                    }
                } else {
                    when {
                        slot.isWrong -> R.drawable.module_learning_bg_practice_slot_wrong
                        slot.isHintLocked -> R.drawable.module_learning_bg_practice_slot_hint
                        else -> R.drawable.module_learning_bg_spelling_slot_empty
                    }
                }
            )
        }
    }

    private fun renderLetters(letters: List<SpellingPracticeViewModel.LetterItem>) {
        if (letters.isEmpty()) {
            databind.gridLetters.removeAllViews()
            letterButtons.clear()
            deleteButton = null
            return
        }
        val columnCount = resolveLetterColumnCount(letters.size + 1)
        val needsRebuild = letterButtons.size != letters.size ||
            letterButtons.zip(letters).any { (button, item) ->
                button.text?.toString() != item.letter.toString()
            }
        if (needsRebuild) {
            databind.gridLetters.removeAllViews()
            letterButtons.clear()
            val rowItems = letters.map { item ->
                createLetterButton(item.letter.toString()).also { button ->
                    letterButtons += button
                }
            } + createDeleteButton()
            rowItems.chunked(columnCount).forEach { rowButtons ->
                databind.gridLetters.addView(createKeyboardRow(rowButtons))
            }
            deleteButton = rowItems.lastOrNull()
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

    private fun createKeyboardRow(buttons: List<MaterialButton>): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            buttons.forEach { button ->
                addView(button, createKeyLayoutParams())
            }
        }
    }

    private fun createDeleteButton(): MaterialButton {
        return createLetterButton("⌫").apply {
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, 0, 0, 1.dpToPx(requireContext()))
            contentDescription = getString(R.string.practice_spelling_delete)
        }
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
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            minWidth = 0
            minHeight = 0
            setTextColor(Color.parseColor("#0F172A"))
            cornerRadius = 10.dpToPx(requireContext())
            strokeWidth = 0
            background = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.module_learning_bg_spelling_key
            )
            backgroundTintList = null
            insetTop = 0
            insetBottom = 0
        }
    }

    private fun resolveLetterColumnCount(itemCount: Int): Int {
        return 5
    }

    private fun createKeyLayoutParams(): LinearLayout.LayoutParams {
        val columnCount = resolveLetterColumnCount(0)
        val horizontalPadding = 36.dpToPx(requireContext())
        val cellMargin = 4.dpToPx(requireContext())
        val availableWidth = resources.displayMetrics.widthPixels - horizontalPadding
        val width = ((availableWidth - cellMargin * 2 * columnCount) / columnCount)
            .coerceAtLeast(42.dpToPx(requireContext()))
        return LinearLayout.LayoutParams(width, 45.dpToPx(requireContext())).apply {
            this.width = width
            setMargins(cellMargin, 4.dpToPx(requireContext()), cellMargin, 4.dpToPx(requireContext()))
        }
    }

    private fun renderActionEmphasis(state: SpellingPracticeViewModel.SpellingUiState) {
        val nextIsPrimary = state.canNext
        databind.btnNextWord.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(if (nextIsPrimary) "#111827" else "#FFFFFF")
        )
        databind.btnNextWord.setTextColor(
            Color.parseColor(if (nextIsPrimary) "#FFFFFF" else "#64748B")
        )
        databind.btnSubmit.alpha = if (state.canSubmit) 1f else 0.45f
        databind.btnNextWord.alpha = if (state.canNext) 1f else 0.55f
    }

    private fun navigateToCompletionIfReady(state: SpellingPracticeViewModel.SpellingUiState) {
        val result = state.completionResult ?: return
        if (!state.isCompleted || completionNavigated) return
        completionNavigated = true
        (activity as? PracticeActivity)?.showSpellingDone(result)
    }

    private fun buildCompactStatusText(
        state: SpellingPracticeViewModel.SpellingUiState
    ): String {
        val progress = state.progressText
            .substringAfter(":", state.progressText)
            .trim()
            .ifBlank { "0/0" }
        val length = if (state.wordLength > 0) {
            "${state.wordLength} 个字母"
        } else {
            "- 个字母"
        }
        return "$progress · $length"
    }

    private fun shakeWrongSlots(state: SpellingPracticeViewModel.SpellingUiState) {
        if (state.wrongShakeRequestId <= 0 ||
            state.wrongShakeRequestId == lastWrongShakeRequestId
        ) {
            return
        }
        lastWrongShakeRequestId = state.wrongShakeRequestId
        state.answerSlots.forEachIndexed { index, slot ->
            if (slot.isWrong) {
                ObjectAnimator.ofFloat(
                    slotViews.getOrNull(index) ?: return@forEachIndexed,
                    View.TRANSLATION_X,
                    0f,
                    (-6).dpToPx(requireContext()).toFloat(),
                    6.dpToPx(requireContext()).toFloat(),
                    (-4).dpToPx(requireContext()).toFloat(),
                    4.dpToPx(requireContext()).toFloat(),
                    0f
                ).apply {
                    duration = 260L
                    start()
                }
            }
        }
    }

    private fun onHandwritingHandleTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                drawerStartY = event.rawY
                drawerStartHeight = databind.handwritingDrawer.height
                drawerWasDragged = false
                view.parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val delta = drawerStartY - event.rawY
                if (kotlin.math.abs(delta) > 4.dpToPx(requireContext())) {
                    drawerWasDragged = true
                }
                val targetHeight = (drawerStartHeight + delta).toInt()
                setHandwritingDrawerHeight(targetHeight.coerceIn(collapsedDrawerHeight(), maxDrawerHeight()))
                updateHandwritingDrawerUi()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.parent?.requestDisallowInterceptTouchEvent(false)
                if (!drawerWasDragged && event.actionMasked == MotionEvent.ACTION_UP) {
                    view.performClick()
                    setHandwritingExpanded(!isHandwritingExpanded, animate = true)
                } else {
                    val midpoint = (collapsedDrawerHeight() + maxDrawerHeight()) / 2
                    setHandwritingExpanded(databind.handwritingDrawer.height >= midpoint, animate = true)
                }
                return true
            }
        }
        return false
    }

    private fun setHandwritingExpanded(expanded: Boolean, animate: Boolean) {
        isHandwritingExpanded = expanded
        val targetHeight = if (expanded) defaultExpandedDrawerHeight() else collapsedDrawerHeight()
        if (animate) {
            ValueAnimator.ofInt(databind.handwritingDrawer.height, targetHeight).apply {
                duration = 180L
                interpolator = DecelerateInterpolator()
                addUpdateListener { animation ->
                    setHandwritingDrawerHeight(animation.animatedValue as Int)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        setHandwritingDrawerHeight(targetHeight)
                        updateHandwritingDrawerUi()
                    }
                })
                start()
            }
        } else {
            setHandwritingDrawerHeight(targetHeight)
        }
        updateHandwritingDrawerUi()
    }

    private fun updateHandwritingDrawerUi() {
        isHandwritingExpanded = databind.handwritingDrawer.height > collapsedDrawerHeight() + 12.dpToPx(requireContext())
        databind.tvHandwritingToggle.text = getString(
            if (isHandwritingExpanded) {
                R.string.practice_spelling_handwriting_collapse
            } else {
                R.string.practice_spelling_handwriting_expand
            }
        )
        databind.handwritingContainer.isVisible = isHandwritingExpanded
    }

    private fun setHandwritingDrawerHeight(height: Int) {
        databind.handwritingDrawer.layoutParams = databind.handwritingDrawer.layoutParams.apply {
            this.height = height
        }
    }

    private fun collapsedDrawerHeight(): Int = 40.dpToPx(requireContext())

    private fun defaultExpandedDrawerHeight(): Int {
        return 190.dpToPx(requireContext()).coerceAtMost(maxDrawerHeight())
    }

    private fun maxDrawerHeight(): Int {
        val rootHeight = databind.root.height.takeIf { it > 0 }
            ?: resources.displayMetrics.heightPixels
        return (rootHeight * 0.45f).toInt().coerceAtLeast(96.dpToPx(requireContext()))
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
