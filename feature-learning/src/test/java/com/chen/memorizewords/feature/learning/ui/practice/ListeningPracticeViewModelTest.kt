package com.chen.memorizewords.feature.learning.ui.practice

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import com.chen.memorizewords.domain.word.model.enums.PartOfSpeech
import com.chen.memorizewords.domain.word.model.word.PronunciationType
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.word.model.word.WordDefinitions
import com.chen.memorizewords.domain.word.model.word.WordExample
import com.chen.memorizewords.domain.word.model.word.WordForm
import com.chen.memorizewords.domain.word.model.word.WordQuickLookupResult
import com.chen.memorizewords.domain.word.model.word.WordRoot
import com.chen.memorizewords.domain.practice.PracticeAvailability
import com.chen.memorizewords.domain.practice.PracticeKind
import com.chen.memorizewords.domain.practice.PracticeReportRepository
import com.chen.memorizewords.domain.practice.PracticeSessionReportRecord
import com.chen.memorizewords.domain.practice.PracticeWordProvider
import com.chen.memorizewords.domain.word.query.WordReadFacade
import com.chen.memorizewords.domain.study.repository.WordLearningRepository
import com.chen.memorizewords.domain.practice.repository.ListeningPracticePreferencesRepository
import com.chen.memorizewords.domain.word.repository.WordRepository
import com.chen.memorizewords.domain.practice.usecase.SynthesizeSpeechUseCase
import com.chen.memorizewords.domain.word.usecase.GenerateMultipleChoiceOptionsUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.GetWordLearningStatesByBookIdUseCase
import com.chen.memorizewords.feature.learning.MainDispatcherRule
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.domain.practice.speech.SpeechAudioOutput
import com.chen.memorizewords.domain.practice.speech.PracticeSpeechSynthesizer
import com.chen.memorizewords.domain.practice.speech.SpeechProviderType
import com.chen.memorizewords.domain.practice.speech.SpeechResult
import com.chen.memorizewords.domain.practice.speech.SpeechTask
import com.chen.memorizewords.domain.practice.speech.WordAudioResult
import com.chen.memorizewords.core.ui.vm.UiEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListeningPracticeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `showing mode switch hint emits single confirm dialog event`() = runTest {
        val viewModel = createViewModel()
        val eventDeferred = backgroundScope.async { viewModel.uiEvent.first() }
        runCurrent()

        viewModel.showModeSwitchHintDialog()
        advanceUntilIdle()

        val event = eventDeferred.await() as UiEvent.Dialog.SingleConfirm
        assertEquals("", event.title)
        assertEquals(
            "\u53f3\u4e0a\u89d2\u53ef\u4ee5\u968f\u65f6\u5207\u6362\u8fa8\u97f3\u9009\u4e49\u6216\u8fa8\u97f3\u62fc\u5199\uff0c\u5207\u6362\u540e\u4f1a\u91cd\u65b0\u5f00\u59cb\u5f53\u524d\u542c\u529b\u6d4b\u8bd5\u3002",
            event.message
        )
        assertEquals("\u786e\u5b9a", event.confirmText)
    }

    @Test
    fun `consume mode switch hint only returns true once`() = runTest {
        val viewModel = createViewModel()

        assertTrue(viewModel.consumeModeSwitchHint())
        assertFalse(viewModel.consumeModeSwitchHint())
    }

    @Test
    fun `selecting correct meaning keeps current card for 500ms before moving next`() = runTest {
        val viewModel = createViewModel()

        viewModel.startSession(ListeningPracticeMode.MEANING)
        advanceUntilIdle()

        val initialState = viewModel.uiState.value
        val correctIndex = initialState.meaningOptions.indexOfFirst { it.isCorrect }
        assertTrue(correctIndex >= 0)
        assertEquals("/al/", initialState.phoneticChipText)

        viewModel.onMeaningOptionSelected(correctIndex)

        val selectedState = viewModel.uiState.value
        assertEquals(correctIndex, selectedState.selectedMeaningIndex)
        assertTrue(selectedState.isMeaningTransitionPending)
        assertTrue(selectedState.showMeaningQuestion)
        assertFalse(selectedState.showStudyState)
        assertEquals(
            ListeningMeaningOptionFeedback.CORRECT,
            selectedState.meaningOptionFeedback.getOrNull(correctIndex)
        )
        assertEquals(0, selectedState.wrongMeaningShakeRequestId)
        assertEquals(null, selectedState.wrongMeaningShakeIndex)
        assertEquals("/al/", selectedState.phoneticChipText)

        advanceTimeBy(499)
        runCurrent()

        val pendingState = viewModel.uiState.value
        assertEquals(correctIndex, pendingState.selectedMeaningIndex)
        assertTrue(pendingState.isMeaningTransitionPending)
        assertTrue(pendingState.showMeaningQuestion)
        assertFalse(pendingState.showStudyState)
        assertEquals(
            ListeningMeaningOptionFeedback.CORRECT,
            pendingState.meaningOptionFeedback.getOrNull(correctIndex)
        )
        assertEquals("/al/", pendingState.phoneticChipText)

        advanceTimeBy(1)
        advanceUntilIdle()

        val nextState = viewModel.uiState.value
        assertFalse(nextState.isMeaningTransitionPending)
        assertTrue(nextState.showMeaningQuestion)
        assertFalse(nextState.showStudyState)
        assertEquals("/be/", nextState.phoneticChipText)
    }

    @Test
    fun `selecting wrong meaning waits longer before entering study state`() = runTest {
        val viewModel = createViewModel()

        viewModel.startSession(ListeningPracticeMode.MEANING)
        advanceUntilIdle()

        val initialState = viewModel.uiState.value
        val wrongIndex = initialState.meaningOptions.indexOfFirst { !it.isCorrect }
        assertTrue(wrongIndex >= 0)

        viewModel.onMeaningOptionSelected(wrongIndex)

        val selectedState = viewModel.uiState.value
        assertEquals(wrongIndex, selectedState.selectedMeaningIndex)
        assertTrue(selectedState.isMeaningTransitionPending)
        assertTrue(selectedState.showMeaningQuestion)
        assertFalse(selectedState.showStudyState)
        val correctIndex = selectedState.meaningOptions.indexOfFirst { it.isCorrect }
        assertTrue(correctIndex >= 0)
        assertEquals(
            ListeningMeaningOptionFeedback.WRONG,
            selectedState.meaningOptionFeedback.getOrNull(wrongIndex)
        )
        assertEquals(
            ListeningMeaningOptionFeedback.CORRECT,
            selectedState.meaningOptionFeedback.getOrNull(correctIndex)
        )
        assertEquals(wrongIndex, selectedState.wrongMeaningShakeIndex)
        assertTrue(selectedState.wrongMeaningShakeRequestId > 0)
        assertEquals("/al/", selectedState.phoneticChipText)

        advanceTimeBy(799)
        runCurrent()

        val pendingState = viewModel.uiState.value
        assertTrue(pendingState.showMeaningQuestion)
        assertFalse(pendingState.showStudyState)
        assertTrue(pendingState.isMeaningTransitionPending)
        assertEquals(
            ListeningMeaningOptionFeedback.WRONG,
            pendingState.meaningOptionFeedback.getOrNull(wrongIndex)
        )
        assertEquals(
            ListeningMeaningOptionFeedback.CORRECT,
            pendingState.meaningOptionFeedback.getOrNull(correctIndex)
        )
        assertEquals("/al/", pendingState.phoneticChipText)

        advanceTimeBy(1)
        advanceUntilIdle()

        val studyState = viewModel.uiState.value
        assertFalse(studyState.isMeaningTransitionPending)
        assertFalse(studyState.showMeaningQuestion)
        assertTrue(studyState.showStudyState)
        assertEquals("alpha", studyState.studyWord)
        assertEquals(wrongIndex, studyState.selectedMeaningIndex)
    }

    @Test
    fun `restarting session cancels pending meaning transition`() = runTest {
        val viewModel = createViewModel()

        viewModel.startSession(ListeningPracticeMode.MEANING)
        advanceUntilIdle()

        val correctIndex = viewModel.uiState.value.meaningOptions.indexOfFirst { it.isCorrect }
        viewModel.onMeaningOptionSelected(correctIndex)
        assertTrue(viewModel.uiState.value.isMeaningTransitionPending)

        viewModel.onModeChanged(ListeningPracticeMode.SPELLING)
        advanceUntilIdle()

        val spellingState = viewModel.uiState.value
        assertFalse(spellingState.isMeaningTransitionPending)
        assertTrue(spellingState.showSpellingQuestion)
        assertFalse(spellingState.showMeaningQuestion)
        assertTrue(spellingState.meaningOptionFeedback.isEmpty())
        assertEquals(0, spellingState.wrongMeaningShakeRequestId)
        assertEquals("/al/", spellingState.phoneticChipText)

        advanceTimeBy(500)
        advanceUntilIdle()

        val afterDelayState = viewModel.uiState.value
        assertFalse(afterDelayState.isMeaningTransitionPending)
        assertTrue(afterDelayState.showSpellingQuestion)
        assertFalse(afterDelayState.showMeaningQuestion)
        assertEquals("/al/", afterDelayState.phoneticChipText)
    }

    @Test
    fun `spelling mode builds stable letter pool with all answer letters`() = runTest {
        val viewModel = createViewModel()

        viewModel.startSession(ListeningPracticeMode.SPELLING)
        advanceUntilIdle()

        val initialState = viewModel.uiState.value
        assertTrue(initialState.showSpellingQuestion)
        assertEquals(5, initialState.spellingSlots.size)
        assertEquals(19, initialState.spellingLetterPool.size)

        val poolCharacters = initialState.spellingLetterPool.map { it.character }
        assertTrue(poolCharacters.containsAll(listOf("a", "l", "p", "h", "a")))
        assertEquals(2, poolCharacters.count { it == "a" })

        val initialOrder = initialState.spellingLetterPool.map { it.id }
        val firstLetterId = initialState.spellingLetterPool.first { it.character == "a" }.id
        viewModel.onSpellingLetterSelected(firstLetterId)

        val selectedState = viewModel.uiState.value
        assertEquals("a", selectedState.spellingInput)
        assertEquals("a", selectedState.spellingSlots.first().character)
        assertEquals(firstLetterId, selectedState.spellingSlots.first().sourceLetterId)
        assertEquals(initialOrder, selectedState.spellingLetterPool.map { it.id })
    }

    @Test
    fun `spelling delete restores repeated letters and resets on skip`() = runTest {
        val viewModel = createViewModel()

        viewModel.loadWithSelection(longArrayOf(3L, 2L), 20)
        viewModel.startSession(ListeningPracticeMode.SPELLING)
        advanceUntilIdle()

        val initialState = viewModel.uiState.value
        assertEquals(6, initialState.spellingSlots.size)
        assertTrue(initialState.spellingLetterPool.count { it.character == "t" } >= 2)
        assertTrue(initialState.spellingLetterPool.count { it.character == "e" } >= 2)
        val firstT = initialState.spellingLetterPool.first { it.character == "t" }
        val secondT = initialState.spellingLetterPool.first { it.character == "t" && it.id != firstT.id }
        viewModel.onSpellingLetterSelected(firstT.id)
        viewModel.onSpellingLetterSelected(secondT.id)

        val filledState = viewModel.uiState.value
        assertEquals("tt", filledState.spellingInput)
        assertEquals(2, filledState.spellingLetterPool.count { it.character == "t" && it.isUsed })

        viewModel.onSpellingDeleteLast()

        val afterDeleteState = viewModel.uiState.value
        assertEquals("t", afterDeleteState.spellingInput)
        assertEquals(1, afterDeleteState.spellingLetterPool.count { it.character == "t" && it.isUsed })

        viewModel.onSkipQuestion()
        advanceUntilIdle()

        val nextState = viewModel.uiState.value
        assertEquals("/le/", nextState.phoneticChipText)
        assertTrue(nextState.showSpellingQuestion)
        assertTrue(nextState.spellingInput.isEmpty())
        assertTrue(nextState.spellingSlots.all { it.sourceLetterId == null })
        assertTrue(nextState.spellingLetterPool.none { it.isUsed })
    }

    @Test
    fun `spelling wrong answer shows correct answer feedback before study state`() = runTest {
        val viewModel = createViewModel()

        viewModel.startSession(ListeningPracticeMode.SPELLING)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val wrongLetterIds = state.spellingLetterPool
            .filter { it.character in listOf("z", "x", "q", "w", "v") }
            .take(state.spellingSlots.size)
            .map { it.id }
        wrongLetterIds.forEach(viewModel::onSpellingLetterSelected)

        viewModel.submitSpellingAnswer()

        val feedbackState = viewModel.uiState.value
        assertTrue(feedbackState.showSpellingQuestion)
        assertFalse(feedbackState.showStudyState)
        assertTrue(feedbackState.showSpellingAnswerFeedback)
        assertEquals(
            List(feedbackState.spellingSlots.size) { ListeningSpellingSlotFeedback.WRONG },
            feedbackState.spellingSlots.map { it.feedback }
        )
        assertEquals(
            feedbackState.spellingSlots.indices.toList(),
            feedbackState.wrongSpellingShakeIndexes
        )
        assertTrue(feedbackState.wrongSpellingShakeRequestId > 0)
        assertEquals(
            "\u6b63\u786e\u7b54\u6848\uff1aalpha",
            feedbackState.spellingAnswerFeedbackText
        )

        advanceTimeBy(1199)
        runCurrent()

        val pendingState = viewModel.uiState.value
        assertTrue(pendingState.showSpellingQuestion)
        assertFalse(pendingState.showStudyState)
        assertEquals(
            "\u6b63\u786e\u7b54\u6848\uff1aalpha",
            pendingState.spellingAnswerFeedbackText
        )

        advanceTimeBy(1)
        advanceUntilIdle()

        val studyState = viewModel.uiState.value
        assertFalse(studyState.showSpellingQuestion)
        assertTrue(studyState.showStudyState)
        assertFalse(studyState.showSpellingAnswerFeedback)
        assertTrue(studyState.wrongSpellingShakeIndexes.isEmpty())
        assertEquals(0, studyState.wrongSpellingShakeRequestId)
        assertEquals("", studyState.spellingAnswerFeedbackText)
    }

    @Test
    fun `spelling wrong answer marks wrong letters and empty slots then clears on edit`() = runTest {
        val viewModel = createViewModel()

        viewModel.startSession(ListeningPracticeMode.SPELLING)
        advanceUntilIdle()

        val firstAId = viewModel.uiState.value.spellingLetterPool
            .first { it.character == "a" && !it.isUsed }
            .id
        viewModel.onSpellingLetterSelected(firstAId)
        val wrongLetterId = viewModel.uiState.value.spellingLetterPool
            .first { !it.isUsed && it.character != "l" }
            .id
        viewModel.onSpellingLetterSelected(wrongLetterId)

        viewModel.submitSpellingAnswer()

        val feedbackState = viewModel.uiState.value
        assertEquals(ListeningSpellingSlotFeedback.DEFAULT, feedbackState.spellingSlots[0].feedback)
        assertEquals(ListeningSpellingSlotFeedback.WRONG, feedbackState.spellingSlots[1].feedback)
        assertEquals(ListeningSpellingSlotFeedback.WRONG, feedbackState.spellingSlots[2].feedback)
        assertEquals(ListeningSpellingSlotFeedback.WRONG, feedbackState.spellingSlots[3].feedback)
        assertEquals(ListeningSpellingSlotFeedback.WRONG, feedbackState.spellingSlots[4].feedback)
        assertEquals(listOf(1, 2, 3, 4), feedbackState.wrongSpellingShakeIndexes)
        assertTrue(feedbackState.wrongSpellingShakeRequestId > 0)
        assertTrue(feedbackState.showSpellingAnswerFeedback)

        viewModel.onSpellingDeleteLast()

        val editedState = viewModel.uiState.value
        assertTrue(
            editedState.spellingSlots.all {
                it.feedback == ListeningSpellingSlotFeedback.DEFAULT
            }
        )
        assertTrue(editedState.wrongSpellingShakeIndexes.isEmpty())
        assertEquals(0, editedState.wrongSpellingShakeRequestId)
        assertFalse(editedState.showSpellingAnswerFeedback)
        assertEquals("", editedState.spellingAnswerFeedbackText)
    }

    @Test
    fun `spelling correct answer does not show correct answer feedback`() = runTest {
        val viewModel = createViewModel()

        viewModel.startSession(ListeningPracticeMode.SPELLING)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        listOf("a", "l", "p", "h", "a").forEach { target ->
            val letterId = viewModel.uiState.value.spellingLetterPool.first { it.character == target && !it.isUsed }.id
            viewModel.onSpellingLetterSelected(letterId)
        }

        viewModel.submitSpellingAnswer()

        val submittedState = viewModel.uiState.value
        assertFalse(submittedState.showSpellingAnswerFeedback)
        assertEquals("", submittedState.spellingAnswerFeedbackText)
    }

    @Test
    fun `study state shows next-word button and keeps first two examples only`() = runTest {
        val viewModel = createViewModel()

        viewModel.startSession(ListeningPracticeMode.MEANING)
        advanceUntilIdle()

        val wrongIndex = viewModel.uiState.value.meaningOptions.indexOfFirst { !it.isCorrect }
        assertTrue(wrongIndex >= 0)

        viewModel.onMeaningOptionSelected(wrongIndex)
        advanceTimeBy(800)
        advanceUntilIdle()

        val studyState = viewModel.uiState.value
        assertTrue(studyState.showStudyState)
        assertEquals("alpha", studyState.studyWord)
        assertEquals("\u7f8e", studyState.studyPhoneticLocaleLabel)
        assertEquals("/al/", studyState.studyPhoneticChipText)
        assertEquals("\u4e0b\u4e00\u8bcd", studyState.primaryButtonText)
        assertEquals(2, studyState.studyExamples.size)
        assertEquals("alpha example one", studyState.studyExamples[0].englishText)
        assertEquals("alpha \u793a\u4f8b\u4e00", studyState.studyExamples[0].chineseText)
        assertEquals("alpha example two", studyState.studyExamples[1].englishText)
        assertEquals("alpha \u793a\u4f8b\u4e8c", studyState.studyExamples[1].chineseText)
        assertEquals(PronunciationType.US, studyState.studyPronunciationType)
        assertEquals("en-US", studyState.studySpeechLocale)
        assertTrue(studyState.studyPhoneticToggleEnabled)
    }

    @Test
    fun `study phonetic toggle switches label phonetic and locale and caches by locale`() = runTest {
        val harness = createHarness()
        val viewModel = harness.viewModel

        viewModel.startSession(ListeningPracticeMode.MEANING)
        advanceUntilIdle()

        val wrongIndex = viewModel.uiState.value.meaningOptions.indexOfFirst { !it.isCorrect }
        viewModel.onMeaningOptionSelected(wrongIndex)
        advanceTimeBy(800)
        advanceUntilIdle()

        val initialStudyState = viewModel.uiState.value
        assertEquals(PronunciationType.US, initialStudyState.studyPronunciationType)
        assertEquals("\u7f8e", initialStudyState.studyPhoneticLocaleLabel)
        assertEquals("/al/", initialStudyState.studyPhoneticChipText)
        assertEquals("en-US", initialStudyState.studySpeechLocale)
        assertEquals(listOf("alpha:en-US"), harness.speechService.wordRequests)

        viewModel.onStudyPhoneticToggle()
        advanceUntilIdle()

        val ukState = viewModel.uiState.value
        assertEquals(PronunciationType.UK, ukState.studyPronunciationType)
        assertEquals("\u82f1", ukState.studyPhoneticLocaleLabel)
        assertEquals("/al-uk/", ukState.studyPhoneticChipText)
        assertEquals("en-GB", ukState.studySpeechLocale)
        assertEquals(listOf("alpha:en-US", "alpha:en-GB"), harness.speechService.wordRequests)

        viewModel.onStudyPhoneticToggle()
        advanceUntilIdle()

        val usState = viewModel.uiState.value
        assertEquals(PronunciationType.US, usState.studyPronunciationType)
        assertEquals("\u7f8e", usState.studyPhoneticLocaleLabel)
        assertEquals("/al/", usState.studyPhoneticChipText)
        assertEquals("en-US", usState.studySpeechLocale)
        assertEquals(listOf("alpha:en-US", "alpha:en-GB"), harness.speechService.wordRequests)
    }

    @Test
    fun `study phonetic toggle resets to default pronunciation on session restart`() = runTest {
        val viewModel = createViewModel()

        viewModel.startSession(ListeningPracticeMode.MEANING)
        advanceUntilIdle()

        val wrongIndex = viewModel.uiState.value.meaningOptions.indexOfFirst { !it.isCorrect }
        viewModel.onMeaningOptionSelected(wrongIndex)
        advanceTimeBy(800)
        advanceUntilIdle()

        viewModel.onStudyPhoneticToggle()
        advanceUntilIdle()
        assertEquals(PronunciationType.UK, viewModel.uiState.value.studyPronunciationType)

        viewModel.onModeChanged(ListeningPracticeMode.SPELLING)
        advanceUntilIdle()
        viewModel.onModeChanged(ListeningPracticeMode.MEANING)
        advanceUntilIdle()

        val restartedWrongIndex = viewModel.uiState.value.meaningOptions.indexOfFirst { !it.isCorrect }
        viewModel.onMeaningOptionSelected(restartedWrongIndex)
        advanceTimeBy(800)
        advanceUntilIdle()

        val restartedStudyState = viewModel.uiState.value
        assertEquals("alpha", restartedStudyState.studyWord)
        assertEquals(PronunciationType.US, restartedStudyState.studyPronunciationType)
        assertEquals("\u7f8e", restartedStudyState.studyPhoneticLocaleLabel)
        assertEquals("/al/", restartedStudyState.studyPhoneticChipText)
        assertEquals("en-US", restartedStudyState.studySpeechLocale)
    }

    @Test
    fun `study phonetic toggle is ignored for single-sided phonetic`() = runTest {
        val viewModel = createViewModel()

        viewModel.loadWithSelection(longArrayOf(2L), 20)
        viewModel.startSession(ListeningPracticeMode.MEANING)
        advanceUntilIdle()

        val wrongIndex = viewModel.uiState.value.meaningOptions.indexOfFirst { !it.isCorrect }
        viewModel.onMeaningOptionSelected(wrongIndex)
        advanceTimeBy(800)
        advanceUntilIdle()

        val betaStudyState = viewModel.uiState.value
        assertEquals("beta", betaStudyState.studyWord)
        assertEquals(PronunciationType.US, betaStudyState.studyPronunciationType)
        assertEquals("\u7f8e", betaStudyState.studyPhoneticLocaleLabel)
        assertEquals("/be/", betaStudyState.studyPhoneticChipText)
        assertEquals("en-US", betaStudyState.studySpeechLocale)
        assertFalse(betaStudyState.studyPhoneticToggleEnabled)

        viewModel.onStudyPhoneticToggle()

        val unchangedState = viewModel.uiState.value
        assertEquals(PronunciationType.US, unchangedState.studyPronunciationType)
        assertEquals("\u7f8e", unchangedState.studyPhoneticLocaleLabel)
        assertEquals("/be/", unchangedState.studyPhoneticChipText)
        assertEquals("en-US", unchangedState.studySpeechLocale)
    }

    @Test
    fun `publishing report exposes focused review and all words in session order`() = runTest {
        val viewModel = createViewModel()

        viewModel.loadWithSelection(longArrayOf(1L), 20)
        viewModel.startSession(ListeningPracticeMode.MEANING)
        advanceUntilIdle()

        val correctIndex = viewModel.uiState.value.meaningOptions.indexOfFirst { it.isCorrect }
        assertTrue(correctIndex >= 0)

        viewModel.onMeaningOptionSelected(correctIndex)
        advanceTimeBy(500)
        advanceUntilIdle()

        val reportState = viewModel.uiState.value
        assertTrue(reportState.showReportState)
        assertEquals(100, reportState.report.accuracyPercent)
        assertEquals("\u5b8c\u6210", reportState.primaryButtonText)
        assertTrue(reportState.report.focusedReviewWords.isEmpty())
        assertEquals(1, reportState.report.allWords.size)
        assertEquals(1L, reportState.report.allWords.first().wordId)
        assertEquals("alpha", reportState.report.allWords.first().word)
        assertEquals("\u963f\u5c14\u6cd5", reportState.report.allWords.first().meaningText)
        assertEquals("en-US", reportState.report.allWords.first().speechLocale)
    }

    @Test
    fun `revealing answer enters study state and persists revealed report semantics`() = runTest {
        val harness = createHarness()
        val viewModel = harness.viewModel

        viewModel.loadWithSelection(longArrayOf(1L), 20)
        viewModel.startSession(ListeningPracticeMode.MEANING)
        advanceUntilIdle()

        viewModel.onRevealAnswer()
        advanceUntilIdle()

        val studyState = viewModel.uiState.value
        assertTrue(studyState.showStudyState)
        assertFalse(studyState.showMeaningQuestion)
        assertEquals("alpha", studyState.studyWord)

        viewModel.onContinueAfterStudy()
        advanceUntilIdle()

        val reviewState = viewModel.uiState.value
        assertTrue(reviewState.showMeaningQuestion)
        assertFalse(reviewState.showStudyState)

        repeat(3) {
            val cycleState = viewModel.uiState.value
            val correctIndex = cycleState.meaningOptions.indexOfFirst { it.isCorrect }
            assertTrue(correctIndex >= 0)
            viewModel.onMeaningOptionSelected(correctIndex)
            advanceTimeBy(500)
            advanceUntilIdle()
        }

        val reportState = viewModel.uiState.value
        assertTrue(reportState.showReportState)
        assertEquals(100, reportState.report.accuracyPercent)
        assertEquals("0", reportState.report.skippedCountText)
        assertEquals(1, reportState.report.focusedReviewWords.size)

        val savedReport = harness.practiceReportRepository.savedRecords.single().report
        assertEquals(3, savedReport.answeredCount)
        assertEquals(3, savedReport.correctCount)
        assertEquals(0, savedReport.wrongCount)
        assertEquals(0, savedReport.skippedCount)
        assertEquals(1, savedReport.revealedCount)
        assertEquals(100, savedReport.accuracyPercent)
    }

    @Test
    fun `skipping question keeps answered count at zero and persists skipped report semantics`() = runTest {
        val harness = createHarness()
        val viewModel = harness.viewModel

        viewModel.loadWithSelection(longArrayOf(1L), 20)
        viewModel.startSession(ListeningPracticeMode.MEANING)
        advanceUntilIdle()

        viewModel.onSkipQuestion()
        advanceUntilIdle()

        val reviewState = viewModel.uiState.value
        assertTrue(reviewState.showMeaningQuestion)
        assertFalse(reviewState.showReportState)

        repeat(3) {
            val cycleState = viewModel.uiState.value
            val correctIndex = cycleState.meaningOptions.indexOfFirst { it.isCorrect }
            assertTrue(correctIndex >= 0)
            viewModel.onMeaningOptionSelected(correctIndex)
            advanceTimeBy(500)
            advanceUntilIdle()
        }

        val reportState = viewModel.uiState.value
        assertTrue(reportState.showReportState)
        assertEquals(100, reportState.report.accuracyPercent)
        assertEquals("1", reportState.report.skippedCountText)
        assertEquals(1, reportState.report.focusedReviewWords.size)

        val savedReport = harness.practiceReportRepository.savedRecords.single().report
        assertEquals(3, savedReport.answeredCount)
        assertEquals(3, savedReport.correctCount)
        assertEquals(0, savedReport.wrongCount)
        assertEquals(1, savedReport.skippedCount)
        assertEquals(0, savedReport.revealedCount)
        assertEquals(100, savedReport.accuracyPercent)
    }

    private fun createViewModel(): ListeningPracticeViewModel = createHarness().viewModel

    private data class TestHarness(
        val viewModel: ListeningPracticeViewModel,
        val speechService: FakeSpeechService,
        val practiceReportRepository: FakePracticeReportRepository
    )

    private fun createHarness(): TestHarness {
        val words = listOf(
            testWord(id = 1L, value = "alpha", phoneticUs = "al", phoneticUk = "al-uk"),
            testWord(id = 2L, value = "beta", phoneticUs = "be"),
            testWord(id = 3L, value = "letter", phoneticUs = "le")
        )
        val definitions = mapOf(
            1L to listOf(
                WordDefinitions(
                    id = 11L,
                    wordId = 1L,
                    partOfSpeech = PartOfSpeech.NOUN,
                    meaningChinese = "\u963f\u5c14\u6cd5"
                )
            ),
            2L to listOf(
                WordDefinitions(
                    id = 21L,
                    wordId = 2L,
                    partOfSpeech = PartOfSpeech.NOUN,
                    meaningChinese = "\u8d1d\u5854"
                )
            ),
            3L to listOf(
                WordDefinitions(
                    id = 31L,
                    wordId = 3L,
                    partOfSpeech = PartOfSpeech.NOUN,
                    meaningChinese = "\u5b57\u6bcd"
                )
            )
        )
        val examples = mapOf(
            1L to listOf(
                WordExample(
                    id = 101L,
                    wordId = 1L,
                    englishSentence = "alpha example one",
                    chineseTranslation = "alpha \u793a\u4f8b\u4e00"
                ),
                WordExample(
                    id = 102L,
                    wordId = 1L,
                    englishSentence = "alpha example two",
                    chineseTranslation = "alpha \u793a\u4f8b\u4e8c"
                ),
                WordExample(
                    id = 103L,
                    wordId = 1L,
                    englishSentence = "alpha example three",
                    chineseTranslation = "alpha \u793a\u4f8b\u4e09"
                )
            ),
            2L to listOf(
                WordExample(
                    id = 201L,
                    wordId = 2L,
                    englishSentence = "beta example",
                    chineseTranslation = "beta \u793a\u4f8b"
                )
            ),
            3L to listOf(
                WordExample(
                    id = 301L,
                    wordId = 3L,
                    englishSentence = "letter example",
                    chineseTranslation = "letter \u793a\u4f8b"
                )
            )
        )
        val speechService = FakeSpeechService()
        val wordRepository = FakeWordRepository(words, definitions, examples)
        val practiceReportRepository = FakePracticeReportRepository()
        return TestHarness(
            viewModel = ListeningPracticeViewModel(
                resourceProvider = FakeResourceProvider(),
                wordReadFacade = WordReadFacade(
                    wordRepository = wordRepository,
                    generateMultipleChoiceOptionsUseCase = GenerateMultipleChoiceOptionsUseCase(
                        wordRepository
                    )
                ),
                synthesizeSpeech = SynthesizeSpeechUseCase(speechService),
                wordProvider = FakePracticeWordProvider(words),
                getWordLearningStatesByBookId = GetWordLearningStatesByBookIdUseCase(
                    FakeWordLearningRepository()
                ),
                listeningPracticePreferencesRepository = FakeListeningPracticePreferencesRepository(),
                practiceReportRepository = practiceReportRepository
            ),
            speechService = speechService,
            practiceReportRepository = practiceReportRepository
        )
    }

    private class FakeResourceProvider : ResourceProvider {
        override fun getString(resId: Int, vararg formatArgs: Any): String {
            return when (resId) {
                R.string.practice_listening_progress_format -> "${formatArgs[0]}/${formatArgs[1]}"
                R.string.practice_listening_review_progress_format ->
                    "review ${formatArgs[0]}/${formatArgs[1]}"
                R.string.practice_listening_phonetic_chip -> formatArgs[0].toString()
                R.string.practice_listening_continue_practice -> "\u4e0b\u4e00\u8bcd"
                R.string.practice_listening_report_accuracy_value -> "${formatArgs[0]}%"
                R.string.practice_listening_report_summary_completed ->
                    "\u5b8c\u6210 ${formatArgs[0]} \u4e2a\u5355\u8bcd"
                R.string.practice_listening_report_summary_attempts ->
                    "\u7d2f\u8ba1\u4f5c\u7b54 ${formatArgs[0]} \u6b21\uff0c\u7b54\u5bf9 ${formatArgs[1]} \u6b21"
                R.string.practice_listening_report_complete -> "\u5b8c\u6210"
                R.string.practice_word_picker_confirm -> "\u786e\u5b9a"
                R.string.practice_listening_select_mode_hint ->
                    "\u53f3\u4e0a\u89d2\u53ef\u4ee5\u968f\u65f6\u5207\u6362\u8fa8\u97f3\u9009\u4e49\u6216\u8fa8\u97f3\u62fc\u5199\uff0c\u5207\u6362\u540e\u4f1a\u91cd\u65b0\u5f00\u59cb\u5f53\u524d\u542c\u529b\u6d4b\u8bd5\u3002"
                R.string.practice_listening_spelling_correct_answer ->
                    "\u6b63\u786e\u7b54\u6848\uff1a${formatArgs[0]}"
                else -> {
                    if (formatArgs.isEmpty()) {
                        resId.toString()
                    } else {
                        "$resId:${formatArgs.joinToString(",")}"
                    }
                }
            }
        }
    }

    private class FakePracticeWordProvider(
        private val words: List<Word>
    ) : PracticeWordProvider {
        override suspend fun loadWords(
            selectedIds: LongArray?,
            randomCount: Int,
            defaultLimit: Int
        ): List<Word> {
            val selectedList = selectedIds?.toList().orEmpty()
            val selectedSet = selectedList.toSet()
            return if (selectedSet.isEmpty()) {
                words
            } else {
                selectedList.mapNotNull { selectedId -> words.firstOrNull { it.id == selectedId } }
            }
        }

        override suspend fun getPracticeAvailability(): PracticeAvailability {
            return PracticeAvailability.AVAILABLE
        }

        override suspend fun resolveBookId(): Long = 1L

        override suspend fun loadReviewWordsForPicker(): List<Word> = emptyList()
    }

    private class FakeListeningPracticePreferencesRepository :
        ListeningPracticePreferencesRepository {

        private var lastModeName: String? = null
        private var hintShown: Boolean = false

        override suspend fun getLastListeningPracticeModeName(): String? = lastModeName

        override suspend fun saveLastListeningPracticeModeName(modeName: String) {
            lastModeName = modeName
        }

        override suspend fun hasShownModeSwitchHint(): Boolean = hintShown

        override suspend fun markModeSwitchHintShown() {
            hintShown = true
        }
    }

    private class FakePracticeReportRepository : PracticeReportRepository {
        private val records = mutableListOf<PracticeSessionReportRecord>()
        val savedRecords: List<PracticeSessionReportRecord>
            get() = records.toList()

        override suspend fun save(record: PracticeSessionReportRecord) {
            records += record
        }

        override suspend fun getLatest(kind: PracticeKind): PracticeSessionReportRecord? {
            return records.lastOrNull { it.kind == kind }
        }

        override suspend fun getBySessionId(sessionId: String): PracticeSessionReportRecord? {
            return records.firstOrNull { it.sessionId == sessionId }
        }
    }

    private class FakeSpeechService : PracticeSpeechSynthesizer {
        val wordRequests = mutableListOf<String>()

        override suspend fun synthesize(task: SpeechTask): SpeechResult {
            return when (task) {
                is SpeechTask.SynthesizeWord -> {
                    wordRequests += "${task.text}:${task.locale}"
                    WordAudioResult(
                        provider = SpeechProviderType.ALIYUN,
                        traceId = "test-trace-${wordRequests.size}",
                        audioOutput = SpeechAudioOutput.UrlOutput(
                            url = "https://example.com/${task.text}-${task.locale}.mp3"
                        ),
                        cacheKey = "${task.text}:${task.locale}",
                        isFromCache = false
                    )
                }

                else -> WordAudioResult(
                    provider = SpeechProviderType.ALIYUN,
                    traceId = "test-trace-generic",
                    audioOutput = SpeechAudioOutput.UrlOutput(
                        url = "https://example.com/generic.mp3"
                    ),
                    cacheKey = "generic",
                    isFromCache = false
                )
            }
        }
    }

    private class FakeWordLearningRepository : WordLearningRepository {
        override suspend fun getLearningStatesByIds(
            wordBookId: Long,
            ids: List<Long>
        ): Map<Long, WordLearningState> = emptyMap()

        override suspend fun getLearningStatesByBookId(bookId: Long): List<WordLearningState> {
            return emptyList()
        }

        override suspend fun getLearnedWordIdsByBook(bookId: Long): List<Long> = emptyList()

        override suspend fun deleteLearningWordByBookId(bookId: Long) = Unit
    }

    private class FakeWordRepository(
        words: List<Word>,
        private val definitionsByWordId: Map<Long, List<WordDefinitions>>,
        private val examplesByWordId: Map<Long, List<WordExample>>
    ) : WordRepository {

        private val wordsById = words.associateBy { it.id }
        private val definitions = definitionsByWordId.values.flatten()

        override suspend fun getWordsByIds(ids: List<Long>): List<Word> {
            return ids.mapNotNull(wordsById::get)
        }

        override suspend fun getWordById(wordId: Long): Word? = wordsById[wordId]

        override suspend fun getWordForms(wordId: Long): List<WordForm> = emptyList()

        override suspend fun getRootWordByWordId(wordId: Long): List<WordRoot> = emptyList()

        override suspend fun getWordExamples(wordId: Long): List<WordExample> {
            return examplesByWordId[wordId].orEmpty()
        }

        override suspend fun getWordDefinitions(wordId: Long): List<WordDefinitions> {
            return definitionsByWordId[wordId].orEmpty()
        }

        override suspend fun getRandomDefinition(wordId: Long): WordDefinitions {
            return definitionsByWordId[wordId].orEmpty().first()
        }

        override suspend fun getRandomDefinitionsByPos(
            wordId: Long,
            limit: Int
        ): List<WordDefinitions> {
            return definitions.filterNot { it.wordId == wordId }.take(limit)
        }

        override suspend fun updateWordStatus(
            bookId: Long,
            word: Word,
            quality: Int
        ): Boolean {
            throw UnsupportedOperationException("Not needed in test")
        }

        override suspend fun setWordAsMastered(bookId: Long, word: Word) {
            throw UnsupportedOperationException("Not needed in test")
        }

        override suspend fun getWordByWordString(word: String): Word? {
            return wordsById.values.firstOrNull { it.word == word }
        }

        override suspend fun lookupWordQuick(
            normalizedWord: String,
            rawWord: String
        ): WordQuickLookupResult {
            throw UnsupportedOperationException("Not needed in test")
        }
    }

    private fun testWord(
        id: Long,
        value: String,
        phoneticUs: String,
        phoneticUk: String? = null
    ): Word {
        return Word(
            id = id,
            word = value,
            normalizedWord = value,
            phoneticUS = phoneticUs,
            phoneticUK = phoneticUk,
            hasIrregularForms = false,
            memoryTip = null,
            mnemonicImageUrl = null,
            memoryAssociations = emptyList(),
            wordFamily = null,
            synonyms = emptyList(),
            antonyms = emptyList(),
            tags = emptyList(),
            notes = null,
            rootMemoryTip = null
        )
    }
}
