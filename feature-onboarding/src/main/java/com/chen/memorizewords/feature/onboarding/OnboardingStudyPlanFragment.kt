package com.chen.memorizewords.feature.onboarding

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.image.WordBookCoverImageLoader
import com.chen.memorizewords.domain.wordbook.model.learning.LearningTestMode
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.repository.WordOrderType
import com.chen.memorizewords.feature.onboarding.databinding.FragmentOnboardingStudyPlanBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat

@AndroidEntryPoint
class OnboardingStudyPlanFragment :
    BaseFragment<OnboardingViewModel, FragmentOnboardingStudyPlanBinding>() {

    private var isSyncingCountInputs = false

    override val viewModel: OnboardingViewModel by lazy {
        ViewModelProvider(requireActivity())[OnboardingViewModel::class.java]
    }

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            viewModel.backToSelectWordBook()
        }
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.lifecycleOwner = viewLifecycleOwner
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
        setupInputs()
        observePlanState()
        observeSubmitError()
    }

    private fun setupInputs() {
        setupCountInput(
            editText = databind.etDailyNewValue,
            onValueChanged = viewModel::updateDailyNewCount,
            currentValueProvider = {
                (viewModel.planUiState.value as? OnboardingPlanUiState.Content)
                    ?.studyPlan
                    ?.dailyNewCount
            }
        )
        setupCountInput(
            editText = databind.etDailyReviewValue,
            onValueChanged = viewModel::updateDailyReviewCount,
            currentValueProvider = {
                (viewModel.planUiState.value as? OnboardingPlanUiState.Content)
                    ?.studyPlan
                    ?.dailyReviewCount
            }
        )
        setupRepeatingStepper(
            button = databind.btnDailyNewDecrease,
            onStep = viewModel::decreaseDailyNewCount
        )
        setupRepeatingStepper(
            button = databind.btnDailyNewIncrease,
            onStep = viewModel::increaseDailyNewCount
        )
        setupRepeatingStepper(
            button = databind.btnDailyReviewDecrease,
            onStep = viewModel::decreaseDailyReviewCount
        )
        setupRepeatingStepper(
            button = databind.btnDailyReviewIncrease,
            onStep = viewModel::increaseDailyReviewCount
        )
        databind.tvBalancedSuggestion.setOnClickListener { viewModel.applyBalancedSuggestion() }
        databind.btnModifyBook.setOnClickListener { viewModel.backToSelectWordBook() }
        databind.cardMeaningMode.setOnClickListener {
            viewModel.updateTestMode(LearningTestMode.MEANING_CHOICE)
        }
        databind.cardSpellingMode.isVisible = false
        databind.cardListeningMode.isVisible = false
        databind.radioOrderRandom.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.updateWordOrderType(WordOrderType.RANDOM)
        }
        databind.radioOrderAlphabeticAsc.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.updateWordOrderType(WordOrderType.ALPHABETIC_ASC)
        }
        databind.radioOrderAlphabeticDesc.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.updateWordOrderType(WordOrderType.ALPHABETIC_DESC)
        }
        databind.radioOrderLengthAsc.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.updateWordOrderType(WordOrderType.LENGTH_ASC)
        }
        databind.radioOrderLengthDesc.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.updateWordOrderType(WordOrderType.LENGTH_DESC)
        }
        databind.btnComplete.setOnClickListener { viewModel.completeStudyPlan() }
        databind.btnRetryPlanState.setOnClickListener { viewModel.retryPlanLoad() }
        databind.btnBackToSelectBook.setOnClickListener { viewModel.backToSelectWordBook() }
    }

    private fun setupCountInput(
        editText: EditText,
        onValueChanged: (Int) -> Unit,
        currentValueProvider: () -> Int?
    ) {
        editText.doAfterTextChanged { value ->
            if (isSyncingCountInputs) return@doAfterTextChanged
            val text = value?.toString().orEmpty()
            if (text.isBlank()) return@doAfterTextChanged
            text.toIntOrNull()?.let(onValueChanged)
        }
        editText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                editText.post(editText::selectAll)
            } else {
                currentValueProvider()?.let { bindCountInput(editText, it, force = true) }
            }
        }
    }

    private fun setupRepeatingStepper(button: View, onStep: () -> Unit) {
        var didRepeat = false
        val repeatRunnable = object : Runnable {
            override fun run() {
                if (!button.isPressed || !button.isEnabled) return
                didRepeat = true
                onStep()
                button.postDelayed(this, STEP_REPEAT_INTERVAL_MS)
            }
        }

        button.setOnClickListener {
            if (didRepeat) {
                didRepeat = false
                return@setOnClickListener
            }
            onStep()
        }
        button.setOnTouchListener { view, event ->
            if (!view.isEnabled) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    didRepeat = false
                    view.animate()
                        .scaleX(0.94f)
                        .scaleY(0.94f)
                        .setDuration(BUTTON_PRESS_ANIMATION_DURATION_MS)
                        .start()
                    view.removeCallbacks(repeatRunnable)
                    view.postDelayed(
                        repeatRunnable,
                        ViewConfiguration.getLongPressTimeout().toLong()
                    )
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(BUTTON_PRESS_ANIMATION_DURATION_MS)
                        .start()
                    view.removeCallbacks(repeatRunnable)
                    if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        didRepeat = false
                    }
                }
            }
            false
        }
    }

    private fun bindCountInput(editText: EditText, value: Int, force: Boolean = false) {
        val targetText = value.toString()
        val currentText = editText.text?.toString().orEmpty()
        if (currentText == targetText) return
        if (!force && editText.hasFocus()) {
            if (currentText.isBlank()) return
            if (currentText.toIntOrNull() == value) return
        }
        isSyncingCountInputs = true
        editText.setText(targetText)
        editText.setSelection(editText.text?.length ?: 0)
        isSyncingCountInputs = false
    }

    private fun observePlanState() {
        lifecycleScope.launch {
            viewModel.planUiState.collect { state ->
                databind.contentScrollView.isVisible = state is OnboardingPlanUiState.Content
                databind.planStateGroup.isVisible = state is OnboardingPlanUiState.Error
                databind.planLoading.isVisible = state is OnboardingPlanUiState.Loading

                when (state) {
                    OnboardingPlanUiState.Loading -> Unit

                    is OnboardingPlanUiState.Error -> {
                        databind.tvPlanStateMessage.text = state.message
                    }

                    is OnboardingPlanUiState.Content -> {
                        bindWordBookInfo(state.wordBook)
                        bindCountInput(databind.etDailyNewValue, state.studyPlan.dailyNewCount)
                        bindCountInput(databind.etDailyReviewValue, state.studyPlan.dailyReviewCount)
                        val isInteractive = !state.isSubmitting
                        databind.btnComplete.isEnabled = isInteractive
                        databind.btnModifyBook.isEnabled = isInteractive
                        databind.btnModifyBook.alpha = if (isInteractive) 1f else 0.45f
                        databind.tvBalancedSuggestion.isEnabled = isInteractive
                        databind.btnDailyNewDecrease.isEnabled = isInteractive
                        databind.btnDailyNewIncrease.isEnabled = isInteractive
                        databind.btnDailyReviewDecrease.isEnabled = isInteractive
                        databind.btnDailyReviewIncrease.isEnabled = isInteractive
                        databind.etDailyNewValue.isEnabled = isInteractive
                        databind.etDailyReviewValue.isEnabled = isInteractive
                        databind.cardMeaningMode.isEnabled = isInteractive
                        databind.cardSpellingMode.isEnabled = false
                        databind.cardListeningMode.isEnabled = false
                        databind.cardMeaningMode.isSelected = true
                        databind.cardSpellingMode.isSelected = false
                        databind.cardListeningMode.isSelected = false
                        bindModeCardEnabledState(databind.cardMeaningMode, isInteractive)
                        databind.radioOrderRandom.isChecked =
                            state.studyPlan.wordOrderType == WordOrderType.RANDOM
                        databind.radioOrderAlphabeticAsc.isChecked =
                            state.studyPlan.wordOrderType == WordOrderType.ALPHABETIC_ASC
                        databind.radioOrderAlphabeticDesc.isChecked =
                            state.studyPlan.wordOrderType == WordOrderType.ALPHABETIC_DESC
                        databind.radioOrderLengthAsc.isChecked =
                            state.studyPlan.wordOrderType == WordOrderType.LENGTH_ASC
                        databind.radioOrderLengthDesc.isChecked =
                            state.studyPlan.wordOrderType == WordOrderType.LENGTH_DESC
                    }
                }
            }
        }
    }

    private fun bindWordBookInfo(wordBook: WordBook) {
        databind.tvBookTitle.text = wordBook.title
        databind.tvBookSummary.text = buildWordBookSummary(wordBook)
        WordBookCoverImageLoader.load(
            imageView = databind.ivBookCover,
            fallbackView = databind.ivBookCoverFallback,
            rawUrl = wordBook.imgUrl
        )
    }

    private fun buildWordBookSummary(wordBook: WordBook): String {
        val category = wordBook.category.ifBlank {
            getString(R.string.feature_onboarding_book_uncategorized)
        }
        val formattedCount = NumberFormat.getIntegerInstance().format(wordBook.totalWords)
        return getString(R.string.feature_onboarding_book_summary, category, formattedCount)
    }

    private fun bindModeCardEnabledState(card: View, isEnabled: Boolean) {
        card.alpha = if (isEnabled) 1f else 0.72f
    }

    private fun observeSubmitError() {
        lifecycleScope.launch {
            viewModel.planSubmitErrorMessage.collect { message ->
                databind.tvSubmitError.isVisible = !message.isNullOrBlank()
                databind.tvSubmitError.text = message.orEmpty()
            }
        }
    }

    private companion object {
        const val STEP_REPEAT_INTERVAL_MS = 120L
        const val BUTTON_PRESS_ANIMATION_DURATION_MS = 120L
    }
}
