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
import androidx.core.view.isVisible
import androidx.gridlayout.widget.GridLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.chen.memorizewords.feature.learning.LinearSpacingItemDecoration
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.adapter.DefinitionsAdapter
import com.chen.memorizewords.feature.learning.adapter.ExamplesAdapter
import com.chen.memorizewords.feature.learning.adapter.FormAdapter
import com.chen.memorizewords.feature.learning.adapter.RootsAdapter
import com.chen.memorizewords.feature.learning.adapter.SynonymsAdapter
import com.chen.memorizewords.feature.learning.databinding.FeatureLearningFragmentPracticeListeningBinding
import com.chen.memorizewords.feature.learning.ui.fragment.WaterfallFlowLayoutManager
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

    init {
        initStudyLists()
    }

    private fun initStudyLists() {
        binding.rvStudyDefinitions.adapter = studyDefinitionsAdapter
        binding.rvStudyDefinitions.layoutManager = LinearLayoutManager(context)
        binding.rvStudyDefinitions.isNestedScrollingEnabled = false
        binding.rvStudyDefinitions.addItemDecoration(LinearSpacingItemDecoration(dp(8)))

        binding.rvStudyExamples.adapter = studyExamplesAdapter
        binding.rvStudyExamples.layoutManager = LinearLayoutManager(context)
        binding.rvStudyExamples.isNestedScrollingEnabled = false
        binding.rvStudyExamples.addItemDecoration(LinearSpacingItemDecoration(dp(16)))

        binding.rvStudyRoot.adapter = studyRootsAdapter
        binding.rvStudyRoot.layoutManager = LinearLayoutManager(context)
        binding.rvStudyRoot.isNestedScrollingEnabled = false
        binding.rvStudyRoot.addItemDecoration(LinearSpacingItemDecoration(dp(16)))

        binding.rvStudySynonyms.adapter = studySynonymsAdapter
        binding.rvStudySynonyms.layoutManager = WaterfallFlowLayoutManager()
        binding.rvStudySynonyms.isNestedScrollingEnabled = false

        binding.rvStudyInflection.adapter = studyFormAdapter
        binding.rvStudyInflection.layoutManager = LinearLayoutManager(context)
        binding.rvStudyInflection.isNestedScrollingEnabled = false
    }

    fun render(state: ListeningPracticeViewModel.ListeningUiState) {
        renderHeader(state)
        binding.tvModeBadge.text = state.modeBadgeText
        binding.tvPhonetic.text = state.phoneticChipText
        binding.tvPrompt.text = state.instructionPrimaryText.ifBlank { state.promptText }
        renderPromptAlignment(state)
        binding.tvPromptHint.text = state.instructionSecondaryText.ifBlank { state.promptHint }
        binding.tvPromptHint.isVisible =
            binding.tvPromptHint.text.isNotBlank() && !state.showReportState

        binding.layoutPracticeRoot.isVisible = !state.showStudyState && !state.showReportState
        binding.layoutStudyRoot.isVisible = state.showStudyState
        binding.layoutReportRoot.isVisible = state.showReportState
        binding.layoutMeaningOptions.isVisible = state.showMeaningQuestion
        binding.layoutSpellingQuestion.isVisible = state.showSpellingQuestion
        binding.layoutPracticeActions.isVisible =
            state.footerMode == ListeningFooterMode.PRACTICE_ACTIONS && !state.showReportState

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
        binding.btnSubmitSpelling.isEnabled = state.spellingSubmitEnabled

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
        binding.btnPrimaryAction.isEnabled = state.primaryButtonEnabled
        if (state.showReportState) {
            binding.btnPrimaryAction.icon =
                AppCompatResources.getDrawable(context, R.drawable.module_learning_check)
            binding.btnPrimaryAction.iconTint = ColorStateList.valueOf(Color.WHITE)
            binding.btnPrimaryAction.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_END
            binding.btnPrimaryAction.iconPadding = dp(8)
        } else {
            binding.btnPrimaryAction.icon = null
            binding.btnPrimaryAction.iconTint = null
            binding.btnPrimaryAction.iconPadding = 0
        }
        binding.layoutBottomActions.isVisible =
            binding.layoutPracticeActions.isVisible || binding.btnPrimaryAction.isVisible

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

    private fun renderHeader(state: ListeningPracticeViewModel.ListeningUiState) {
        if (state.showReportState) {
            binding.tvScreenTitle.text =
                context.getString(R.string.practice_listening_report_header_title)
            binding.btnSettings.setImageResource(R.drawable.module_learning_share_24dp_444f5f)
            binding.btnSettings.isEnabled = false
            binding.btnSettings.isClickable = false
            binding.btnSettings.alpha = 1f
            return
        }
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
        button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_END
        button.iconPadding = dp(12)
        button.iconSize = dp(24)
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
        button.strokeWidth = dp(strokeWidthDp)
        button.text = text
        button.setTextColor(Color.parseColor(textColor))
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
                        dp(18),
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    minHeight = dp(24)
                    gravity = Gravity.CENTER
                    textSize = 16f
                    setTypeface(typeface, Typeface.NORMAL)
                }
            )
            addView(
                View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(18), dp(1)).apply {
                        topMargin = dp(6)
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
            LinearLayout.LayoutParams(dp(18), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (!isLast) {
                    marginEnd = dp(8)
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
        grid.columnCount = SPELLING_GRID_COLUMN_COUNT
        grid.rowCount =
            ((letters.size + 1 + SPELLING_GRID_COLUMN_COUNT - 1) / SPELLING_GRID_COLUMN_COUNT)
        val activeIds = letters.mapTo(mutableSetOf()) { it.id }
        spellingLetterButtons.keys.removeAll { it !in activeIds }
        letters.forEachIndexed { index, letter ->
            val button = spellingLetterButtons.getOrPut(letter.id) { buildSpellingLetterButton() }
            updateSpellingLetterButton(
                button = button,
                letter = letter,
                practiceInteractionEnabled = practiceInteractionEnabled
            )
            grid.addView(button, createSpellingGridLayoutParams(index))
        }
        val deleteButton = spellingDeleteButton ?: buildSpellingDeleteButton().also {
            spellingDeleteButton = it
        }
        updateSpellingDeleteButton(
            button = deleteButton,
            practiceInteractionEnabled = practiceInteractionEnabled,
            hasSelectedLetters = hasSelectedLetters
        )
        grid.addView(deleteButton, createSpellingGridLayoutParams(letters.size))
    }

    private fun createSpellingGridLayoutParams(index: Int): GridLayout.LayoutParams {
        val row = index / SPELLING_GRID_COLUMN_COUNT
        val column = index % SPELLING_GRID_COLUMN_COUNT
        return GridLayout.LayoutParams(
            GridLayout.spec(row, GridLayout.CENTER),
            GridLayout.spec(column, GridLayout.CENTER)
        ).apply {
            width = dp(44)
            height = dp(44)
            setGravity(Gravity.CENTER)
            setMargins(dp(6), dp(6), dp(6), dp(6))
        }
    }

    private fun buildSpellingLetterButton(): MaterialButton {
        return MaterialButton(context).apply {
            layoutParams = ViewGroup.LayoutParams(dp(44), dp(44))
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
            cornerRadius = dp(22)
            elevation = dp(1).toFloat()
            strokeWidth = dp(1)
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
            layoutParams = ViewGroup.LayoutParams(dp(44), dp(44))
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            cornerRadius = dp(22)
            elevation = dp(1).toFloat()
            strokeWidth = dp(1)
            icon = AppCompatResources.getDrawable(context, R.drawable.module_learning_clear)
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
            setPadding(dp(16), dp(20), dp(16), dp(20))
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
                    topMargin = dp(10)
                }
            }
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = Color.parseColor("#EEF2F6")
            setCardBackgroundColor(Color.WHITE)
            preventCornerOverlap = true
            useCompatPadding = false

            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(15), dp(14), dp(15))

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
                                        topMargin = dp(6)
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
                            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                                marginStart = dp(12)
                            }
                            radius = dp(20).toFloat()
                            cardElevation = 0f
                            strokeWidth = dp(1)
                            strokeColor = Color.parseColor("#E6EBF2")
                            setCardBackgroundColor(Color.parseColor("#F8FAFC"))
                            isClickable = true
                            isFocusable = true
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
                                    setPadding(dp(10), dp(10), dp(10), dp(10))
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
        val largeOffset = dp(16).toFloat()
        val mediumOffset = dp(12).toFloat()
        val smallOffset = dp(8).toFloat()
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
        val largeOffset = dp(12).toFloat()
        val mediumOffset = dp(8).toFloat()
        val smallOffset = dp(5).toFloat()
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

    private fun dp(value: Int): Int {
        return (value * binding.root.resources.displayMetrics.density).toInt()
    }

    private val context get() = binding.root.context

    private inner class MeaningPartOfSpeechSpan : ReplacementSpan() {
        private val columnWidth = dp(52)

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

    private companion object {
        const val SPELLING_GRID_COLUMN_COUNT = 5
    }
}
