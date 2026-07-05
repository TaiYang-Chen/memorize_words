package com.chen.memorizewords.feature.learning.ui.practice.listening.renderer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.gridlayout.widget.GridLayout
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.chen.memorizewords.core.ui.ext.dpToPx
import com.chen.memorizewords.domain.practice.ListeningAnswerAreaPosition
import com.chen.memorizewords.domain.practice.ListeningPronunciationPreference
import com.chen.memorizewords.feature.learning.LinearSpacingItemDecoration
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.adapter.DefinitionsAdapter
import com.chen.memorizewords.feature.learning.adapter.ExamplesAdapter
import com.chen.memorizewords.feature.learning.adapter.FormAdapter
import com.chen.memorizewords.feature.learning.adapter.RootsAdapter
import com.chen.memorizewords.feature.learning.adapter.SynonymsAdapter
import com.chen.memorizewords.feature.learning.databinding.FeatureLearningFragmentPracticeListeningBinding
import com.chen.memorizewords.feature.learning.ui.practice.ListeningFeedbackTone
import com.chen.memorizewords.feature.learning.ui.practice.ListeningFooterMode
import com.chen.memorizewords.feature.learning.ui.practice.ListeningMeaningOptionFeedback
import com.chen.memorizewords.feature.learning.ui.practice.ListeningMeaningOptionUi
import com.chen.memorizewords.feature.learning.ui.practice.ListeningPracticeViewModel
import com.chen.memorizewords.feature.learning.ui.practice.ListeningQuestionType
import com.chen.memorizewords.feature.learning.ui.practice.ListeningReportWordUi
import com.chen.memorizewords.feature.learning.ui.practice.ListeningSpellingLetterUi
import com.chen.memorizewords.feature.learning.ui.practice.ListeningSpellingSlotFeedback
import com.chen.memorizewords.feature.learning.ui.practice.ListeningSpellingSlotUi
import com.chen.memorizewords.feature.learning.ui.visibleWordExamples
import com.chen.memorizewords.feature.learning.ui.visibleWordForms
import com.chen.memorizewords.feature.learning.ui.visibleWordRoots
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.FlexWrap
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

internal class ListeningPracticeRenderer(
    private val binding: FeatureLearningFragmentPracticeListeningBinding,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onSpellingLetterSelected(letterId: Long)
        fun onSpellingDeleteLast()
        fun onStudyExampleAudioRequested(index: Int)
        fun onStudySentenceAudioRequested(sentence: String)
        fun onReportWordAudioRequested(row: ListeningReportWordUi)
        fun onAutoPlayRequested()
    }

    private val studyDefinitionsAdapter = DefinitionsAdapter()
    private val studyExamplesAdapter = ExamplesAdapter(
        onclickWord = { _, _ -> },
        onSpeakSentence = { sentence -> callbacks.onStudySentenceAudioRequested(sentence) }
    )
    private val studyRootsAdapter = RootsAdapter()
    private val studySynonymsAdapter = SynonymsAdapter()
    private val studyFormAdapter = FormAdapter()
    private var lastAutoPlayRequestId: Int = -1
    private var lastWrongMeaningShakeRequestId: Int = -1
    private var lastWrongSpellingShakeRequestId: Int = -1
    private val spellingSlotViews = mutableListOf<View>()
    private val spellingLetterButtons = mutableMapOf<Long, MaterialButton>()
    private var spellingDeleteButton: MaterialButton? = null
    private val baseScrollBottomPadding = binding.scrollContent.paddingBottom
    private val baseBottomActionsBottomPadding = binding.layoutBottomActions.paddingBottom
    private var navigationBarBottomInset: Int = 0

    init {
        initStudyLists()
        initInsets()
        initAccessibility()
    }

    private fun initStudyLists() {
        binding.rvStudyDefinitions.adapter = studyDefinitionsAdapter
        binding.rvStudyDefinitions.layoutManager = LinearLayoutManager(context)
        binding.rvStudyDefinitions.isNestedScrollingEnabled = false
        binding.rvStudyDefinitions.addItemDecoration(LinearSpacingItemDecoration(8.dpToPx(context)))

        binding.rvStudyExamples.adapter = studyExamplesAdapter
        binding.rvStudyExamples.layoutManager = LinearLayoutManager(context)
        binding.rvStudyExamples.isNestedScrollingEnabled = false
        binding.rvStudyExamples.addItemDecoration(LinearSpacingItemDecoration(16.dpToPx(context)))

        binding.rvStudyRoot.adapter = studyRootsAdapter
        binding.rvStudyRoot.layoutManager = LinearLayoutManager(context)
        binding.rvStudyRoot.isNestedScrollingEnabled = false
        binding.rvStudyRoot.addItemDecoration(LinearSpacingItemDecoration(16.dpToPx(context)))

        binding.rvStudySynonyms.adapter = studySynonymsAdapter
        binding.rvStudySynonyms.layoutManager = FlexboxLayoutManager(context).apply {
            flexDirection = FlexDirection.ROW
            flexWrap = FlexWrap.WRAP
        }
        binding.rvStudySynonyms.isNestedScrollingEnabled = false

        binding.rvStudyInflection.adapter = studyFormAdapter
        binding.rvStudyInflection.layoutManager = LinearLayoutManager(context)
        binding.rvStudyInflection.isNestedScrollingEnabled = false
    }

    private fun initInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            navigationBarBottomInset =
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            binding.layoutBottomActions.updatePadding(
                bottom = baseBottomActionsBottomPadding + navigationBarBottomInset
            )
            updateScrollContentBottomPadding()
            insets
        }
        binding.layoutBottomActions.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateScrollContentBottomPadding()
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun initAccessibility() {
        binding.btnRevealAnswer.contentDescription =
            context.getString(R.string.practice_listening_action_reveal)
        binding.btnSkip.contentDescription =
            context.getString(R.string.practice_listening_action_skip)
        binding.btnSubmitSpelling.contentDescription =
            context.getString(R.string.practice_listening_submit)
        binding.btnPlayStudyAudio.contentDescription =
            context.getString(R.string.practice_listening_play_audio)
    }

    fun render(state: ListeningPracticeViewModel.ListeningUiState) {
        renderHeader(state)
        binding.progressHeader.max = state.progressMax.coerceAtLeast(1)
        binding.progressHeader.setProgressCompat(
            state.progressValue.coerceAtLeast(0),
            false
        )
        binding.tvModeBadge.text = state.modeTitle.ifBlank { state.modeBadgeText }
        binding.tvPhonetic.text = state.phoneticChipText
        renderPracticePronunciationPreference(state.pronunciationPreference)
        binding.tvPrompt.text = state.instructionPrimaryText.ifBlank { state.promptText }
        renderAnswerAreaPosition(state)
        renderPromptAlignment(state)
        binding.tvPromptHint.text = state.instructionSecondaryText.ifBlank { state.promptHint }
        binding.tvPromptHint.isVisible =
            binding.tvPromptHint.text.isNotBlank() && !state.showReportState

        binding.layoutPracticeRoot.isVisible = !state.showStudyState && !state.showReportState
        binding.layoutStudyRoot.isVisible = state.showStudyState
        binding.layoutReportRoot.isVisible = state.showReportState
        binding.layoutMeaningOptions.isVisible = state.showMeaningQuestion
        binding.layoutSpellingQuestion.isVisible = state.showSpellingQuestion
        renderScrollBehavior(state)
        val showPracticeActions =
            state.footerMode == ListeningFooterMode.PRACTICE_ACTIONS && !state.showReportState
        val showSpellingSubmit =
            showPracticeActions && state.showSpellingQuestion && !state.showStudyState
        binding.btnSubmitSpelling.isVisible = showSpellingSubmit
        binding.layoutPracticeActions.isVisible = showPracticeActions

        binding.tvFeedback.isVisible = state.feedbackMessage.isNotBlank()
        binding.tvFeedback.text = state.feedbackMessage
        renderFeedback(state.feedbackTone)

        val practiceInteractionEnabled = !state.loading && !state.isMeaningTransitionPending
        binding.btnPlayAudio.isEnabled = practiceInteractionEnabled
        binding.btnPlayStudyAudio.isEnabled = !state.loading
        binding.btnRevealAnswer.isEnabled = practiceInteractionEnabled
        binding.btnSkip.isEnabled = practiceInteractionEnabled

        renderOptionButton(
            binding.btnOption1,
            state.meaningOptions.getOrNull(0),
            state.meaningOptionFeedback.getOrNull(0) ?: ListeningMeaningOptionFeedback.DEFAULT,
            practiceInteractionEnabled
        )
        renderOptionButton(
            binding.btnOption2,
            state.meaningOptions.getOrNull(1),
            state.meaningOptionFeedback.getOrNull(1) ?: ListeningMeaningOptionFeedback.DEFAULT,
            practiceInteractionEnabled
        )
        renderOptionButton(
            binding.btnOption3,
            state.meaningOptions.getOrNull(2),
            state.meaningOptionFeedback.getOrNull(2) ?: ListeningMeaningOptionFeedback.DEFAULT,
            practiceInteractionEnabled
        )
        renderOptionButton(
            binding.btnOption4,
            state.meaningOptions.getOrNull(3),
            state.meaningOptionFeedback.getOrNull(3) ?: ListeningMeaningOptionFeedback.DEFAULT,
            practiceInteractionEnabled
        )

        renderSpellingSlots(state.spellingSlots)
        binding.tvSpellingAnswerFeedback.isVisible =
            state.showSpellingQuestion &&
                state.showSpellingAnswerFeedback &&
                state.spellingAnswerFeedbackText.isNotBlank()
        binding.tvSpellingAnswerFeedback.text = state.spellingAnswerFeedbackText
        binding.tvSpellingAnswerFeedback.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor("#FDECEF"))
        renderSpellingLetterPool(
            letters = state.spellingLetterPool,
            practiceInteractionEnabled = practiceInteractionEnabled,
            hasSelectedLetters = state.spellingSlots.any { it.sourceLetterId != null }
        )
        binding.btnSubmitSpelling.isEnabled =
            showSpellingSubmit && state.spellingSubmitEnabled && practiceInteractionEnabled
        binding.btnSubmitSpelling.alpha = if (binding.btnSubmitSpelling.isEnabled) 1f else 0.45f

        binding.tvStudyWord.text = state.studyWord
        val checkedPronunciationId = when (state.studyPronunciationType) {
            com.chen.memorizewords.domain.word.model.word.PronunciationType.US -> R.id.btn_study_us
            com.chen.memorizewords.domain.word.model.word.PronunciationType.UK -> R.id.btn_study_uk
        }
        if (binding.studyLanguageRadioGroup.checkedRadioButtonId != checkedPronunciationId) {
            binding.studyLanguageRadioGroup.check(checkedPronunciationId)
        }
        binding.btnStudyUs.isEnabled = true
        binding.btnStudyUk.isEnabled = true
        binding.tvStudyPhonetic.text = state.studyPhoneticChipText
        binding.layoutStudyPhoneticBar.isVisible = state.studyPhoneticChipText.isNotBlank()
        studyDefinitionsAdapter.submitGroupedDefinitions(state.studyDefinitions)
        binding.layoutStudyMemoryTip.isVisible = state.studyMemoryTip.isNotBlank()
        binding.tvStudyMemoryTip.text = state.studyMemoryTip
        val studyExamples = state.studyExamples.visibleWordExamples()
        studyExamplesAdapter.submitList(studyExamples)
        setOptionalSectionVisible(
            binding.sectionStudyExamplesHeader,
            binding.rvStudyExamples,
            studyExamples.isNotEmpty()
        )
        val studyForms = state.studyForms.visibleWordForms()
        studyFormAdapter.submitList(studyForms)
        setOptionalSectionVisible(
            binding.sectionStudyInflectionHeader,
            binding.rvStudyInflection,
            studyForms.isNotEmpty()
        )
        val studyRoots = state.studyRoots.visibleWordRoots()
        studyRootsAdapter.submitList(studyRoots)
        setOptionalSectionVisible(
            binding.sectionStudyRootsHeader,
            binding.rvStudyRoot,
            studyRoots.isNotEmpty()
        )
        val hasStudyRelations = studySynonymsAdapter.submitRelations(
            state.studySynonyms,
            state.studyAntonyms
        )
        setOptionalSectionVisible(
            binding.sectionStudySynonymsHeader,
            binding.rvStudySynonyms,
            hasStudyRelations
        )

        renderReportState(state)

        binding.btnPrimaryAction.isVisible =
            state.footerMode == ListeningFooterMode.PRIMARY_ACTION &&
                state.primaryButtonText.isNotBlank()
        binding.btnPrimaryAction.text = state.primaryButtonText
        binding.btnPrimaryAction.contentDescription = state.primaryButtonText
        binding.btnPrimaryAction.isEnabled = state.primaryButtonEnabled
        if (state.showReportState) {
            binding.btnPrimaryAction.icon =
                AppCompatResources.getDrawable(context, R.drawable.module_learning_check)
            binding.btnPrimaryAction.iconTint = ColorStateList.valueOf(Color.WHITE)
            binding.btnPrimaryAction.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_END
            binding.btnPrimaryAction.iconPadding = 8.dpToPx(context)
        } else {
            binding.btnPrimaryAction.icon = null
            binding.btnPrimaryAction.iconTint = null
            binding.btnPrimaryAction.iconPadding = 0
        }
        binding.layoutBottomActions.isVisible =
            binding.layoutPracticeActions.isVisible ||
                binding.btnSubmitSpelling.isVisible ||
                binding.btnPrimaryAction.isVisible
        updateScrollContentBottomPadding()

        triggerPendingAnimations(state)
        triggerAutoPlay(state)
    }

    private fun renderPromptAlignment(state: ListeningPracticeViewModel.ListeningUiState) {
        val isPracticeSpellingPrompt =
            !state.showStudyState &&
                !state.showReportState &&
                state.questionType == ListeningQuestionType.SPELLING
        binding.tvPrompt.gravity = if (isPracticeSpellingPrompt) {
            Gravity.CENTER
        } else {
            Gravity.START or Gravity.CENTER_VERTICAL
        }
    }

    private fun renderPracticePronunciationPreference(
        preference: ListeningPronunciationPreference
    ) {
        val checkedPronunciationId = when (preference) {
            ListeningPronunciationPreference.US -> R.id.btn_practice_us
            ListeningPronunciationPreference.UK -> R.id.btn_practice_uk
        }
        if (binding.practiceLanguageRadioGroup.checkedRadioButtonId != checkedPronunciationId) {
            binding.practiceLanguageRadioGroup.check(checkedPronunciationId)
        }
    }

    private fun renderAnswerAreaPosition(state: ListeningPracticeViewModel.ListeningUiState) {
        val topMargin = when (state.answerAreaPosition) {
            ListeningAnswerAreaPosition.TOP -> 8.dpToPx(context)
            ListeningAnswerAreaPosition.MIDDLE -> 56.dpToPx(context)
            ListeningAnswerAreaPosition.BOTTOM -> resolveBottomAnswerAreaMargin(state)
        }
        val params = binding.layoutAnswerArea.layoutParams as? LinearLayout.LayoutParams
            ?: return
        if (params.topMargin == topMargin) return
        binding.layoutAnswerArea.layoutParams = params.apply {
            this.topMargin = topMargin
        }
    }

    private fun renderScrollBehavior(state: ListeningPracticeViewModel.ListeningUiState) {
        val allowUserScroll = state.showStudyState || state.showReportState
        binding.scrollContent.isUserScrollEnabled = allowUserScroll
    }

    private fun resolveBottomAnswerAreaMargin(
        state: ListeningPracticeViewModel.ListeningUiState
    ): Int {
        if (!state.showSpellingQuestion) return 168.dpToPx(context)
        val letterItemCount = state.spellingLetterPool.size + 1
        val columnCount = resolveSpellingGridColumnCount(letterItemCount)
        val rowCount = ((letterItemCount + columnCount - 1) / columnCount).coerceAtLeast(1)
        return when {
            rowCount >= 4 -> 96.dpToPx(context)
            rowCount == 3 -> 132.dpToPx(context)
            else -> 148.dpToPx(context)
        }
    }

    private fun renderHeader(state: ListeningPracticeViewModel.ListeningUiState) {
        if (state.showReportState) {
            binding.tvScreenTitle.text =
                context.getString(R.string.practice_listening_report_header_title)
            binding.progressHeader.isVisible = false
            binding.btnSettings.setImageResource(R.drawable.module_learning_share_24dp_444f5f)
            binding.btnSettings.isEnabled = false
            binding.btnSettings.isClickable = false
            binding.btnSettings.alpha = 1f
            return
        }
        binding.progressHeader.isVisible = true
        binding.tvScreenTitle.text = state.screenTitleText
        binding.btnSettings.setImageResource(R.drawable.module_learning_ic_settings)
        binding.btnSettings.isEnabled = true
        binding.btnSettings.isClickable = true
        binding.btnSettings.alpha = 1f
    }

    private fun renderFeedback(tone: ListeningFeedbackTone) {
        val (textColor, backgroundColor) = when (tone) {
            ListeningFeedbackTone.SUCCESS -> "#0F766E" to "#D1FAE5"
            ListeningFeedbackTone.ERROR -> "#B91C1C" to "#FEE2E2"
            ListeningFeedbackTone.INFO -> "#0F4C81" to "#DBEAFE"
            ListeningFeedbackTone.NONE -> "#5B7286" to "#F1F5F9"
        }
        binding.tvFeedback.setTextColor(Color.parseColor(textColor))
        binding.tvFeedback.setBackgroundResource(R.drawable.module_learning_bg_listening_chip)
        binding.tvFeedback.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor(backgroundColor))
    }

    private fun renderOptionButton(
        button: MaterialButton,
        option: ListeningMeaningOptionUi?,
        feedback: ListeningMeaningOptionFeedback,
        isEnabled: Boolean
    ) {
        if (option == null) {
            button.isVisible = false
            return
        }
        button.isVisible = true
        button.isEnabled = isEnabled
        button.iconGravity = MaterialButton.ICON_GRAVITY_END
        button.iconPadding = 0
        button.iconSize = 24.dpToPx(context)
        when (feedback) {
            ListeningMeaningOptionFeedback.CORRECT ->
                applyMeaningOptionStyle(
                    button = button,
                    text = buildMeaningOptionText(option),
                    backgroundColor = "#4CAF50",
                    strokeColor = "#388E3C",
                    textColor = "#FFFFFF",
                    iconRes = R.drawable.module_learning_check,
                    iconTint = "#FFFFFF",
                    strokeWidthDp = 2
                )

            ListeningMeaningOptionFeedback.WRONG ->
                applyMeaningOptionStyle(
                    button = button,
                    text = buildMeaningOptionText(option),
                    backgroundColor = "#E57373",
                    strokeColor = "#D32F2F",
                    textColor = "#FFFFFF",
                    iconRes = R.drawable.module_learning_clear,
                    iconTint = "#FFFFFF",
                    strokeWidthDp = 2
                )

            ListeningMeaningOptionFeedback.DEFAULT ->
                applyMeaningOptionStyle(
                    button = button,
                    text = buildMeaningOptionText(option),
                    backgroundColor = "#F5F5F5",
                    strokeColor = "#00000000",
                    textColor = "#000000",
                    iconRes = null,
                    strokeWidthDp = 0
                )
        }
    }

    private fun buildMeaningOptionText(option: ListeningMeaningOptionUi): CharSequence {
        val partOfSpeech = option.partOfSpeech.trim()
        val content = option.content.trim()
        if (partOfSpeech.isBlank()) {
            return content
        }

        val text = "$partOfSpeech$content"
        return SpannableString(text).apply {
            setSpan(
                MeaningPartOfSpeechSpan(),
                0,
                partOfSpeech.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun applyMeaningOptionStyle(
        button: MaterialButton,
        text: CharSequence,
        backgroundColor: String,
        strokeColor: String,
        textColor: String,
        iconRes: Int?,
        iconTint: String? = null,
        strokeWidthDp: Int
    ) {
        button.backgroundTintList = ColorStateList.valueOf(Color.parseColor(backgroundColor))
        button.strokeColor = ColorStateList.valueOf(Color.parseColor(strokeColor))
        button.strokeWidth = (strokeWidthDp).dpToPx(context)
        button.text = text
        button.setTextColor(Color.parseColor(textColor))
        button.setPaddingRelative(20.dpToPx(context), 0, if (iconRes == null) 20.dpToPx(context) else 54.dpToPx(context), 0)
        button.icon = iconRes?.let { AppCompatResources.getDrawable(button.context, it) }
        button.iconTint = iconTint?.let { ColorStateList.valueOf(Color.parseColor(it)) }
    }

    private fun renderSpellingSlots(slots: List<ListeningSpellingSlotUi>) {
        val container = binding.layoutSpellingSlots
        if (container.childCount != slots.size) {
            container.removeAllViews()
            spellingSlotViews.clear()
            slots.forEach { _ ->
                val slotView = buildReusableSpellingSlotView()
                spellingSlotViews += slotView
                container.addView(slotView)
            }
        }
        slots.forEachIndexed { index, slot ->
            val slotView = container.getChildAt(index) as LinearLayout
            updateSpellingSlotView(slotView, slot, isLast = index == slots.lastIndex)
            if (spellingSlotViews.getOrNull(index) !== slotView) {
                spellingSlotViews.add(index, slotView)
            }
        }
    }

    private fun buildReusableSpellingSlotView(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        18.dpToPx(context),
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    minHeight = 24.dpToPx(context)
                    gravity = Gravity.CENTER
                    textSize = 16f
                    setTypeface(typeface, Typeface.NORMAL)
                }
            )
            addView(
                View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(18.dpToPx(context), 1.dpToPx(context)).apply {
                        topMargin = 6.dpToPx(context)
                    }
                }
            )
        }
    }

    private fun updateSpellingSlotView(
        slotView: LinearLayout,
        slot: ListeningSpellingSlotUi,
        isLast: Boolean
    ) {
        slotView.layoutParams =
            FlexboxLayout.LayoutParams(20.dpToPx(context), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 2.dpToPx(context)
                bottomMargin = 4.dpToPx(context)
                if (!isLast) {
                    marginEnd = 6.dpToPx(context)
                }
            }
        val isWrong = slot.feedback == ListeningSpellingSlotFeedback.WRONG
        val textView = slotView.getChildAt(0) as TextView
        textView.text = slot.character
        textView.setTextColor(
            Color.parseColor(
                when {
                    isWrong -> "#D32F2F"
                    slot.sourceLetterId == null -> "#8A93A2"
                    else -> "#111827"
                }
            )
        )
        slotView.getChildAt(1)
            .setBackgroundColor(Color.parseColor(if (isWrong) "#D32F2F" else "#D7DDE7"))
    }

    private fun renderSpellingLetterPool(
        letters: List<ListeningSpellingLetterUi>,
        practiceInteractionEnabled: Boolean,
        hasSelectedLetters: Boolean
    ) {
        val grid = binding.gridSpellingLetters
        grid.removeAllViews()
        val columnCount = resolveSpellingGridColumnCount(letters.size + 1)
        grid.columnCount = columnCount
        grid.rowCount =
            ((letters.size + 1 + columnCount - 1) / columnCount)
        val activeIds = letters.mapTo(mutableSetOf()) { it.id }
        spellingLetterButtons.keys.removeAll { it !in activeIds }
        letters.forEachIndexed { index, letter ->
            val button = spellingLetterButtons.getOrPut(letter.id) { buildSpellingLetterButton() }
            updateSpellingLetterButton(
                button = button,
                letter = letter,
                practiceInteractionEnabled = practiceInteractionEnabled
            )
            grid.addView(button, createSpellingGridLayoutParams(index, columnCount))
        }
        val deleteButton = spellingDeleteButton ?: buildSpellingDeleteButton().also {
            spellingDeleteButton = it
        }
        updateSpellingDeleteButton(
            button = deleteButton,
            practiceInteractionEnabled = practiceInteractionEnabled,
            hasSelectedLetters = hasSelectedLetters
        )
        grid.addView(deleteButton, createSpellingGridLayoutParams(letters.size, columnCount))
    }

    private fun resolveSpellingGridColumnCount(itemCount: Int): Int {
        val availableWidth = (binding.scrollContent.width - 32.dpToPx(context)).coerceAtLeast(0)
        return when {
            itemCount >= 16 && availableWidth >= 300.dpToPx(context) -> 6
            availableWidth < 300.dpToPx(context) -> 4
            else -> 5
        }
    }

    private fun createSpellingGridLayoutParams(
        index: Int,
        columnCount: Int
    ): GridLayout.LayoutParams {
        val row = index / columnCount
        val column = index % columnCount
        val buttonSize = if (columnCount >= 6) 40.dpToPx(context) else 44.dpToPx(context)
        val buttonMargin = if (columnCount >= 6) 5.dpToPx(context) else 6.dpToPx(context)
        return GridLayout.LayoutParams(
            GridLayout.spec(row, GridLayout.CENTER),
            GridLayout.spec(column, GridLayout.CENTER)
        ).apply {
            width = buttonSize
            height = buttonSize
            setGravity(Gravity.CENTER)
            setMargins(buttonMargin, buttonMargin, buttonMargin, buttonMargin)
        }
    }

    private fun buildSpellingLetterButton(): MaterialButton {
        return MaterialButton(context).apply {
            layoutParams = ViewGroup.LayoutParams(44.dpToPx(context), 44.dpToPx(context))
            insetTop = 0
            insetBottom = 0
            icon = null
            textSize = 14f
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            isAllCaps = false
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            cornerRadius = 22.dpToPx(context)
            elevation = 1.dpToPx(context).toFloat()
            strokeWidth = 1.dpToPx(context)
        }
    }

    private fun updateSpellingLetterButton(
        button: MaterialButton,
        letter: ListeningSpellingLetterUi,
        practiceInteractionEnabled: Boolean
    ) {
        button.text = letter.character
        button.setOnClickListener { callbacks.onSpellingLetterSelected(letter.id) }
        applySpellingLetterStyle(
            button = button,
            isUsed = letter.isUsed,
            isEnabled = practiceInteractionEnabled && !letter.isUsed
        )
    }

    private fun buildSpellingDeleteButton(): MaterialButton {
        return MaterialButton(context).apply {
            layoutParams = ViewGroup.LayoutParams(44.dpToPx(context), 44.dpToPx(context))
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            cornerRadius = 22.dpToPx(context)
            elevation = 1.dpToPx(context).toFloat()
            strokeWidth = 1.dpToPx(context)
            icon = AppCompatResources.getDrawable(context, R.drawable.module_learning_clear)
            contentDescription = context.getString(R.string.practice_listening_spelling_delete)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = 0
            iconTint = ColorStateList.valueOf(Color.parseColor("#4B5563"))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
            strokeColor = ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
            setOnClickListener { callbacks.onSpellingDeleteLast() }
        }
    }

    private fun updateSpellingDeleteButton(
        button: MaterialButton,
        practiceInteractionEnabled: Boolean,
        hasSelectedLetters: Boolean
    ) {
        button.isEnabled = practiceInteractionEnabled && hasSelectedLetters
        button.alpha = if (button.isEnabled) 1f else 0.28f
    }

    private fun applySpellingLetterStyle(
        button: MaterialButton,
        isUsed: Boolean,
        isEnabled: Boolean
    ) {
        val textColor = if (isUsed) "#C7CFDB" else "#111827"
        button.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
        button.strokeColor = ColorStateList.valueOf(Color.parseColor("#E5E7EB"))
        button.setTextColor(Color.parseColor(textColor))
        button.isEnabled = isEnabled
        button.alpha = if (isUsed) 0.35f else 1f
    }

    private fun setOptionalSectionVisible(header: View, list: View, visible: Boolean) {
        header.isVisible = visible
        list.isVisible = visible
    }

    private fun renderReportState(state: ListeningPracticeViewModel.ListeningUiState) {
        binding.tvReportTitle.text = state.promptText
        binding.tvReportHint.text = state.promptHint
        binding.tvReportHint.isVisible = state.promptHint.isNotBlank()
        binding.progressReportAccuracy.setProgressCompat(state.report.accuracyPercent, false)
        binding.tvReportAccuracy.text = state.report.accuracyText
        binding.tvReportSummaryPrimary.text = state.report.summaryPrimaryText
        binding.tvReportSummarySecondary.text = state.report.summarySecondaryText
        binding.tvReportSummarySecondary.isVisible = state.report.summarySecondaryText.isNotBlank()
        renderReportWordCards(
            binding.layoutReportReviewedWords,
            state.report.focusedReviewWords,
            emptyTextRes = R.string.practice_listening_report_reviewed_empty
        )
        renderReportWordCards(
            binding.layoutReportUnfinishedWords,
            state.report.allWords,
            emptyTextRes = R.string.practice_listening_report_all_words_empty
        )
    }

    private fun renderReportWordCards(
        container: LinearLayout,
        rows: List<ListeningReportWordUi>,
        emptyTextRes: Int
    ) {
        container.removeAllViews()
        if (rows.isEmpty()) {
            container.addView(buildReportEmptyView(emptyTextRes))
            return
        }
        rows.forEachIndexed { index, row ->
            container.addView(buildReportWordCardView(row, index))
        }
    }

    private fun buildReportEmptyView(emptyTextRes: Int): View {
        return TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
            setPadding(16.dpToPx(context), 20.dpToPx(context), 16.dpToPx(context), 20.dpToPx(context))
            text = context.getString(emptyTextRes)
            textSize = 12f
            setTextColor(Color.parseColor("#8C96A8"))
            background = AppCompatResources.getDrawable(
                context,
                R.drawable.module_learning_bg_listening_card_surface
            )
        }
    }

    private fun buildReportWordCardView(
        row: ListeningReportWordUi,
        index: Int
    ): View {
        return MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) {
                    topMargin = 10.dpToPx(context)
                }
            }
            radius = 18.dpToPx(context).toFloat()
            cardElevation = 0f
            strokeWidth = 1.dpToPx(context)
            strokeColor = Color.parseColor("#EEF2F6")
            setCardBackgroundColor(Color.WHITE)
            preventCornerOverlap = true
            useCompatPadding = false

            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(16.dpToPx(context), 15.dpToPx(context), 14.dpToPx(context), 15.dpToPx(context))

                    addView(
                        LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                1f
                            )

                            addView(
                                TextView(context).apply {
                                    text = row.word
                                    textSize = 18f
                                    setTypeface(typeface, Typeface.BOLD)
                                    setTextColor(Color.parseColor("#10213A"))
                                }
                            )

                            addView(
                                TextView(context).apply {
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    ).apply {
                                        topMargin = 6.dpToPx(context)
                                    }
                                    text = row.meaningText
                                    textSize = 12f
                                    setTextColor(Color.parseColor("#6B7280"))
                                }
                            )
                        }
                    )

                    addView(
                        MaterialCardView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(40.dpToPx(context), 40.dpToPx(context)).apply {
                                marginStart = 12.dpToPx(context)
                            }
                            radius = 20.dpToPx(context).toFloat()
                            cardElevation = 0f
                            strokeWidth = 1.dpToPx(context)
                            strokeColor = Color.parseColor("#E6EBF2")
                            setCardBackgroundColor(Color.parseColor("#F8FAFC"))
                            isClickable = true
                            isFocusable = true
                            contentDescription =
                                context.getString(R.string.practice_listening_play_audio)
                            setOnClickListener { callbacks.onReportWordAudioRequested(row) }

                            addView(
                                ImageView(context).apply {
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    background = AppCompatResources.getDrawable(
                                        context,
                                        android.R.drawable.list_selector_background
                                    )
                                    setPadding(10.dpToPx(context), 10.dpToPx(context), 10.dpToPx(context), 10.dpToPx(context))
                                    setImageDrawable(
                                        AppCompatResources.getDrawable(
                                            context,
                                            R.drawable.module_learning_ic_volume_up_gray
                                        )
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }
    }

    private fun triggerPendingAnimations(state: ListeningPracticeViewModel.ListeningUiState) {
        if (
            state.wrongMeaningShakeRequestId > 0 &&
            state.wrongMeaningShakeRequestId != lastWrongMeaningShakeRequestId
        ) {
            lastWrongMeaningShakeRequestId = state.wrongMeaningShakeRequestId
            state.wrongMeaningShakeIndex
                ?.let(::meaningButtonAt)
                ?.let(::animateWrongMeaningSelection)
        }

        if (
            state.wrongSpellingShakeRequestId > 0 &&
            state.wrongSpellingShakeRequestId != lastWrongSpellingShakeRequestId
        ) {
            lastWrongSpellingShakeRequestId = state.wrongSpellingShakeRequestId
            state.wrongSpellingShakeIndexes.forEach { index ->
                spellingSlotViews.getOrNull(index)?.let(::animateWrongSpellingSlot)
            }
        }
    }

    private fun triggerAutoPlay(state: ListeningPracticeViewModel.ListeningUiState) {
        if (state.autoPlayRequestId > 0 && state.autoPlayRequestId != lastAutoPlayRequestId) {
            lastAutoPlayRequestId = state.autoPlayRequestId
            callbacks.onAutoPlayRequested()
        }
    }

    private fun meaningButtonAt(index: Int): MaterialButton? {
        return when (index) {
            0 -> binding.btnOption1
            1 -> binding.btnOption2
            2 -> binding.btnOption3
            3 -> binding.btnOption4
            else -> null
        }
    }

    private fun animateWrongMeaningSelection(button: MaterialButton) {
        button.animate().cancel()
        button.clearAnimation()
        button.translationX = 0f
        val largeOffset = 16.dpToPx(context).toFloat()
        val mediumOffset = 12.dpToPx(context).toFloat()
        val smallOffset = 8.dpToPx(context).toFloat()
        ObjectAnimator.ofFloat(
            button,
            View.TRANSLATION_X,
            0f,
            -largeOffset,
            largeOffset,
            -mediumOffset,
            mediumOffset,
            -smallOffset,
            smallOffset,
            0f
        ).apply {
            duration = 400L
            interpolator = LinearInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    button.translationX = 0f
                }

                override fun onAnimationCancel(animation: Animator) {
                    button.translationX = 0f
                }
            })
            start()
        }
    }

    private fun animateWrongSpellingSlot(slotView: View) {
        slotView.animate().cancel()
        slotView.clearAnimation()
        slotView.translationX = 0f
        val largeOffset = 12.dpToPx(context).toFloat()
        val mediumOffset = 8.dpToPx(context).toFloat()
        val smallOffset = 5.dpToPx(context).toFloat()
        ObjectAnimator.ofFloat(
            slotView,
            View.TRANSLATION_X,
            0f,
            -largeOffset,
            largeOffset,
            -mediumOffset,
            mediumOffset,
            -smallOffset,
            smallOffset,
            0f
        ).apply {
            duration = 320L
            interpolator = LinearInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    slotView.translationX = 0f
                }

                override fun onAnimationCancel(animation: Animator) {
                    slotView.translationX = 0f
                }
            })
            start()
        }
    }

    private fun updateScrollContentBottomPadding() {
        binding.scrollContent.updatePadding(
            bottom = baseScrollBottomPadding +
                if (binding.layoutBottomActions.isVisible) 12.dpToPx(context) else 0
        )
    }

    private val context get() = binding.root.context

    private inner class MeaningPartOfSpeechSpan : ReplacementSpan() {
        private val columnWidth = 52.dpToPx(context)

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            return columnWidth
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val oldTypeface = paint.typeface
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.isAntiAlias = true
            canvas.drawText(text, start, end, x, y.toFloat(), paint)
            paint.typeface = oldTypeface
        }
    }
}
