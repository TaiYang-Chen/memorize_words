package com.chen.memorizewords.feature.learning.ui.practice

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.model.words.word.WordDefinitions
import com.chen.memorizewords.domain.practice.PracticeWordProvider
import com.chen.memorizewords.domain.usecase.practice.EvaluateShadowingUseCase
import com.chen.memorizewords.domain.usecase.practice.SynthesizeSpeechUseCase
import com.chen.memorizewords.domain.usecase.word.GetWordDefinitionsUseCase
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.speech.api.ShadowingAnalysisSource
import com.chen.memorizewords.speech.api.ShadowingAudioIssueType
import com.chen.memorizewords.speech.api.ShadowingEvaluationResult
import com.chen.memorizewords.speech.api.SpeechAudioInput
import com.chen.memorizewords.speech.api.ShadowingRecordingMetadata
import com.chen.memorizewords.speech.api.SpeechAudioSuccess
import com.chen.memorizewords.speech.api.SpeechFailureResult
import com.chen.memorizewords.speech.api.SpeechTask
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ShadowingStage {
    WAITING,
    RECORDING,
    EVALUATING,
    FEEDBACK,
    COMPLETED
}

data class ShadowingAttemptUi(
    val attemptIndex: Int,
    val title: String,
    val scoreText: String,
    val recognizedText: String,
    val guidanceText: String,
    val audioFilePath: String,
    val waveformSamples: List<Int>,
    val totalScore: Int? = null,
    val pronunciationScore: Int? = null,
    val fluencyScore: Int? = null,
    val intonationScore: Int? = null,
    val stressScore: Int? = null,
    val speedScore: Int? = null,
    val scoreBreakdownText: String = "",
    val audioIssueText: String = "",
    val detailSourceNote: String = ""
)

@HiltViewModel
class ShadowingPracticeViewModel @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val synthesizeSpeech: SynthesizeSpeechUseCase,
    private val evaluateShadowing: EvaluateShadowingUseCase,
    private val wordProvider: PracticeWordProvider,
    private val getWordDefinitions: GetWordDefinitionsUseCase
) : BaseViewModel() {

    data class ShadowingUiState(
        val loading: Boolean = true,
        val stage: ShadowingStage = ShadowingStage.WAITING,
        val questionIndex: Int = 0,
        val questionCount: Int = 0,
        val progressText: String = "0/0",
        val wordId: Long = -1L,
        val word: String = "",
        val phoneticText: String = "",
        val meaningText: String = "",
        val speech: SpeechAudioSuccess? = null,
        val latestAttempt: ShadowingAttemptUi? = null,
        val attemptHistory: List<ShadowingAttemptUi> = emptyList(),
        val feedbackTitle: String = "",
        val feedbackMessage: String = "",
        val statusTitle: String = "",
        val statusSubtitle: String = "",
        val summaryText: String = "",
        val pendingReviewText: String = "",
        val isInReview: Boolean = false,
        val reviewTagText: String = "",
        val scoreBreakdownText: String = "",
        val audioIssueText: String = "",
        val detailSourceNote: String = "",
        val isCompleted: Boolean = false,
        val nextActionText: String = "",
        val autoPlaySequenceId: Int = 0,
        val summary: PracticeSessionSummary = PracticeSessionSummary()
    )

    private data class AttemptRecord(
        val audioFilePath: String,
        val waveformSamples: List<Int>,
        val durationMs: Long,
        val evaluation: ShadowingEvaluationResult? = null,
        val errorMessage: String = ""
    )

    private data class WordRuntime(
        val word: Word,
        var definitions: List<WordDefinitions> = emptyList(),
        var speech: SpeechAudioSuccess? = null,
        val attempts: MutableList<AttemptRecord> = mutableListOf(),
        var reviewRequired: Boolean = false,
        var reviewEnqueued: Boolean = false,
        var isCurrentReviewRound: Boolean = false,
        var autoReviewCount: Int = 0
    )

    private enum class QueueType {
        NEW,
        REVIEW
    }

    private val _uiState = MutableStateFlow(ShadowingUiState())
    val uiState: StateFlow<ShadowingUiState> = _uiState.asStateFlow()

    private val _sessionWordIds = MutableStateFlow<List<Long>>(emptyList())
    val sessionWordIds: StateFlow<List<Long>> = _sessionWordIds.asStateFlow()

    private var loadKey: String? = null
    private var runtimes: List<WordRuntime> = emptyList()
    private val newQueue = ArrayDeque<Int>()
    private val reviewQueue = ArrayDeque<Int>()
    private var currentRuntimeIndex: Int? = null
    private var renderRequestToken: Int = 0
    private var evaluateRequestToken: Int = 0
    private var preferReviewNext: Boolean = false

    fun loadWithSelection(selectedIds: LongArray?, randomCount: Int) {
        val newLoadKey = buildPracticeSelectionKey(selectedIds, randomCount)
        if (loadKey == newLoadKey) return
        loadKey = newLoadKey
        loadWords(selectedIds, randomCount)
    }

    fun onBackClick() {
        back()
    }

    fun onGuideClick() {
        val state = _uiState.value
        if (state.word.isBlank()) return
        val title = resourceProvider.getString(R.string.practice_shadowing_guide_title)
        val sections = buildList {
            if (state.phoneticText.isNotBlank()) add(state.phoneticText)
            if (state.meaningText.isNotBlank()) add(state.meaningText)
            if (state.feedbackMessage.isNotBlank()) add(state.feedbackMessage)
            if (state.latestAttempt?.recognizedText?.isNotBlank() == true) {
                add(
                    resourceProvider.getString(
                        R.string.practice_shadowing_dialog_recognized,
                        state.latestAttempt.recognizedText
                    )
                )
            }
            if (state.scoreBreakdownText.isNotBlank()) add(state.scoreBreakdownText)
            if (state.audioIssueText.isNotBlank()) add(state.audioIssueText)
            if (state.detailSourceNote.isNotBlank()) add(state.detailSourceNote)
        }
        showConfirmDialog(
            title = title,
            message = sections.joinToString(separator = "\n\n")
        )
    }

    fun onRecordingStarted() {
        val runtime = currentRuntime() ?: return
        if (_uiState.value.loading || _uiState.value.isCompleted) return
        _uiState.value = buildUiState(
            runtime = runtime,
            stage = ShadowingStage.RECORDING,
            loading = false,
            autoPlaySequenceId = _uiState.value.autoPlaySequenceId
        )
    }

    fun onRecordingFailed(message: String) {
        val runtime = currentRuntime() ?: return
        showToast(message)
        _uiState.value = buildUiState(
            runtime = runtime,
            stage = ShadowingStage.WAITING,
            loading = false,
            autoPlaySequenceId = _uiState.value.autoPlaySequenceId
        )
    }

    fun onRecordingCompleted(
        audioFilePath: String,
        waveformSamples: List<Int>,
        durationMs: Long
    ) {
        val runtimeIndex = currentRuntimeIndex ?: return
        val runtime = runtimes.getOrNull(runtimeIndex) ?: return
        val attempt = AttemptRecord(
            audioFilePath = audioFilePath,
            waveformSamples = waveformSamples.ifEmpty { listOf(16, 24, 18, 30, 20, 26, 14, 22) },
            durationMs = durationMs
        )
        runtime.attempts += attempt
        val autoPlaySequenceId = _uiState.value.autoPlaySequenceId + 1
        _uiState.value = buildUiState(
            runtime = runtime,
            stage = ShadowingStage.EVALUATING,
            loading = false,
            autoPlaySequenceId = autoPlaySequenceId
        )
        evaluateAttempt(runtimeIndex = runtimeIndex, attemptIndex = runtime.attempts.lastIndex)
    }

    fun retryCurrentWord() {
        val runtime = currentRuntime() ?: return
        if (_uiState.value.loading || _uiState.value.stage == ShadowingStage.RECORDING) return
        _uiState.value = buildUiState(
            runtime = runtime,
            stage = ShadowingStage.WAITING,
            loading = false,
            autoPlaySequenceId = _uiState.value.autoPlaySequenceId
        )
    }

    fun nextWord() {
        if (_uiState.value.loading || _uiState.value.stage == ShadowingStage.RECORDING) return
        if (_uiState.value.isCompleted) {
            finish()
            return
        }
        val runtimeIndex = currentRuntimeIndex ?: return
        val runtime = runtimes.getOrNull(runtimeIndex) ?: return
        if (runtime.attempts.isEmpty()) {
            enqueueReview(runtimeIndex)
        }
        openNextWord()
    }

    private fun evaluateAttempt(runtimeIndex: Int, attemptIndex: Int) {
        val runtime = runtimes.getOrNull(runtimeIndex) ?: return
        val wordText = runtime.word.word.trim()
        if (wordText.isBlank()) {
            _uiState.value = buildUiState(
                runtime = runtime,
                stage = ShadowingStage.FEEDBACK,
                loading = false,
                autoPlaySequenceId = _uiState.value.autoPlaySequenceId
            )
            return
        }
        val requestToken = nextShadowingPracticeRequestToken(evaluateRequestToken)
        evaluateRequestToken = requestToken
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                evaluateShadowing(
                    SpeechTask.EvaluateShadowing(
                        referenceText = wordText,
                        audioInput = SpeechAudioInput.FileInput(
                            runtime.attempts[attemptIndex].audioFilePath
                        ),
                        recordingMetadata = ShadowingRecordingMetadata(
                            durationMs = runtime.attempts[attemptIndex].durationMs,
                            waveformSamples = runtime.attempts[attemptIndex].waveformSamples
                        )
                    )
                )
            }
            val activeRuntimeIndex = currentRuntimeIndex
            if (
                activeRuntimeIndex != runtimeIndex ||
                evaluateRequestToken != requestToken
            ) {
                return@launch
            }
            val latestRuntime = runtimes.getOrNull(runtimeIndex) ?: return@launch
            val oldAttempt = latestRuntime.attempts.getOrNull(attemptIndex) ?: return@launch
            val updatedAttempt = when (result) {
                is ShadowingEvaluationResult -> oldAttempt.copy(
                    evaluation = result,
                    errorMessage = ""
                )

                is SpeechFailureResult -> oldAttempt.copy(
                    evaluation = null,
                    errorMessage = result.message
                        ?: resourceProvider.getString(
                            R.string.practice_shadowing_scoring_unavailable
                        )
                )

                else -> oldAttempt
            }
            latestRuntime.attempts[attemptIndex] = updatedAttempt
            val needsReview = shouldScheduleReview(
                evaluation = updatedAttempt.evaluation,
                errorMessage = updatedAttempt.errorMessage
            )
            if (needsReview) {
                latestRuntime.reviewRequired = true
                enqueueReview(runtimeIndex)
            } else {
                clearReview(latestRuntime)
            }
            _uiState.value = buildUiState(
                runtime = latestRuntime,
                stage = ShadowingStage.FEEDBACK,
                loading = false,
                autoPlaySequenceId = _uiState.value.autoPlaySequenceId
            )
        }
    }

    private fun loadWords(selectedIds: LongArray?, randomCount: Int) {
        viewModelScope.launch {
            _uiState.value = ShadowingUiState(loading = true)
            val words = wordProvider.loadWords(
                selectedIds = selectedIds,
                randomCount = randomCount,
                defaultLimit = 30
            )
            runtimes = words.map { WordRuntime(word = it) }
            _sessionWordIds.value = words.map { it.id }
            newQueue.clear()
            reviewQueue.clear()
            runtimes.indices.forEach(newQueue::addLast)
            currentRuntimeIndex = null
            preferReviewNext = false
            renderRequestToken = nextShadowingPracticeRequestToken(renderRequestToken)
            evaluateRequestToken = nextShadowingPracticeRequestToken(evaluateRequestToken)
            openNextWord()
        }
    }

    private fun openNextWord() {
        val next = selectNextWord()
        if (next == null) {
            renderCompletedState()
            return
        }
        currentRuntimeIndex = next.first
        val queueType = next.second
        val runtime = runtimes.getOrNull(next.first) ?: run {
            renderCompletedState()
            return
        }
        runtime.isCurrentReviewRound = queueType == QueueType.REVIEW
        renderCurrentWord(next.first)
    }

    private fun selectNextWord(): Pair<Int, QueueType>? {
        if (reviewQueue.isNotEmpty() && (preferReviewNext || newQueue.isEmpty())) {
            while (reviewQueue.isNotEmpty()) {
                val index = reviewQueue.removeFirst()
                val runtime = runtimes.getOrNull(index) ?: continue
                runtime.reviewEnqueued = false
                preferReviewNext = false
                return index to QueueType.REVIEW
            }
        }
        if (newQueue.isNotEmpty()) {
            preferReviewNext = reviewQueue.isNotEmpty()
            return newQueue.removeFirst() to QueueType.NEW
        }
        while (reviewQueue.isNotEmpty()) {
            val index = reviewQueue.removeFirst()
            val runtime = runtimes.getOrNull(index) ?: continue
            runtime.reviewEnqueued = false
            preferReviewNext = false
            return index to QueueType.REVIEW
        }
        return null
    }

    private fun renderCurrentWord(runtimeIndex: Int) {
        val runtime = runtimes.getOrNull(runtimeIndex) ?: return
        val requestToken = nextShadowingPracticeRequestToken(renderRequestToken)
        renderRequestToken = requestToken
        _uiState.value = buildUiState(
            runtime = runtime,
            stage = ShadowingStage.WAITING,
            loading = true,
            autoPlaySequenceId = _uiState.value.autoPlaySequenceId
        )
        viewModelScope.launch {
            val definitionsTask = async(Dispatchers.IO) {
                if (runtime.definitions.isNotEmpty()) {
                    runtime.definitions
                } else {
                    getWordDefinitions(runtime.word.id)
                }
            }
            val speechTask = async(Dispatchers.IO) {
                runtime.speech ?: (synthesizeSpeech(
                    SpeechTask.SynthesizeWord(text = runtime.word.word)
                ) as? SpeechAudioSuccess)
            }
            runtime.definitions = definitionsTask.await()
            runtime.speech = speechTask.await()
            if (
                currentRuntimeIndex != runtimeIndex ||
                renderRequestToken != requestToken
            ) {
                return@launch
            }
            _uiState.value = buildUiState(
                runtime = runtime,
                stage = if (runtime.attempts.isEmpty()) {
                    ShadowingStage.WAITING
                } else {
                    ShadowingStage.FEEDBACK
                },
                loading = false,
                autoPlaySequenceId = _uiState.value.autoPlaySequenceId
            )
        }
    }

    private fun renderCompletedState() {
        val summary = buildSummary()
        _uiState.value = ShadowingUiState(
            loading = false,
            stage = ShadowingStage.COMPLETED,
            questionCount = runtimes.size,
            progressText = if (runtimes.isEmpty()) "0/0" else "${runtimes.size}/${runtimes.size}",
            feedbackTitle = resourceProvider.getString(R.string.practice_session_completed),
            feedbackMessage = summaryText(summary),
            statusTitle = resourceProvider.getString(R.string.practice_shadowing_finish_title),
            statusSubtitle = resourceProvider.getString(R.string.practice_shadowing_finish_subtitle),
            summaryText = summaryText(summary),
            pendingReviewText = pendingReviewText(),
            reviewTagText = "",
            isCompleted = true,
            nextActionText = resourceProvider.getString(R.string.practice_shadowing_finish_action),
            autoPlaySequenceId = _uiState.value.autoPlaySequenceId,
            summary = summary
        )
    }

    private fun buildUiState(
        runtime: WordRuntime,
        stage: ShadowingStage,
        loading: Boolean,
        autoPlaySequenceId: Int
    ): ShadowingUiState {
        val attempts = runtime.attempts.mapIndexed { index, attempt ->
            attempt.toUi(
                index = index,
                word = runtime.word.word,
                resourceProvider = resourceProvider
            )
        }
        val latestAttempt = attempts.lastOrNull()
        val summary = buildSummary()
        val pendingReviewText = pendingReviewText()
        val nextActionText = when {
            stage == ShadowingStage.COMPLETED -> {
                resourceProvider.getString(R.string.practice_shadowing_finish_action)
            }

            isFinalAction(runtime) -> resourceProvider.getString(R.string.practice_shadowing_finish_action)
            else -> resourceProvider.getString(R.string.practice_next_word)
        }
        val phonetic = buildPhonetic(runtime.word)
        val meaning = buildMeaning(runtime.definitions)
        val feedback = buildFeedback(runtime.word.word, latestAttempt)
        val statusPair = buildStatus(stage = stage, runtime = runtime)
        return ShadowingUiState(
            loading = loading,
            stage = stage,
            questionIndex = (runtimes.indexOf(runtime) + 1).coerceAtLeast(1),
            questionCount = runtimes.size,
            progressText = if (runtimes.isEmpty()) {
                "0/0"
            } else {
                "${runtimes.indexOf(runtime) + 1}/${runtimes.size}"
            },
            wordId = runtime.word.id,
            word = runtime.word.word,
            phoneticText = phonetic,
            meaningText = meaning,
            speech = runtime.speech,
            latestAttempt = latestAttempt,
            attemptHistory = attempts,
            feedbackTitle = feedback.first,
            feedbackMessage = feedback.second,
            statusTitle = statusPair.first,
            statusSubtitle = statusPair.second,
            summaryText = summaryText(summary),
            pendingReviewText = pendingReviewText,
            isInReview = runtime.isCurrentReviewRound || runtime.reviewRequired,
            reviewTagText = buildReviewTagText(runtime),
            scoreBreakdownText = latestAttempt?.scoreBreakdownText.orEmpty(),
            audioIssueText = latestAttempt?.audioIssueText.orEmpty(),
            detailSourceNote = latestAttempt?.detailSourceNote.orEmpty(),
            isCompleted = false,
            nextActionText = nextActionText,
            autoPlaySequenceId = autoPlaySequenceId,
            summary = summary
        )
    }

    private fun buildSummary(): PracticeSessionSummary {
        val completedCount = runtimes.count { it.attempts.isNotEmpty() }
        val correctCount = runtimes.count { runtime ->
            runtime.attempts.maxOfOrNull { it.evaluation?.totalScore ?: 0 } ?: 0 >= PASS_SCORE
        }
        val submitCount = runtimes.sumOf { it.attempts.size }
        return PracticeSessionSummary(
            questionCount = runtimes.size,
            completedCount = completedCount,
            correctCount = correctCount,
            submitCount = submitCount
        )
    }

    private fun enqueueReview(runtimeIndex: Int) {
        val runtime = runtimes.getOrNull(runtimeIndex) ?: return
        runtime.reviewRequired = true
        if (runtime.reviewEnqueued) return
        if (runtime.autoReviewCount >= MAX_AUTO_REVIEW_ROUNDS) return
        runtime.reviewEnqueued = true
        runtime.autoReviewCount += 1
        reviewQueue.addLast(runtimeIndex)
    }

    private fun clearReview(runtime: WordRuntime) {
        runtime.reviewRequired = false
        runtime.reviewEnqueued = false
        runtime.autoReviewCount = 0
    }

    private fun currentRuntime(): WordRuntime? {
        return currentRuntimeIndex?.let { runtimes.getOrNull(it) }
    }

    private fun isFinalAction(runtime: WordRuntime): Boolean {
        return runtime.attempts.isNotEmpty() &&
            newQueue.isEmpty() &&
            reviewQueue.isEmpty()
    }

    private fun pendingReviewText(): String {
        val pendingCount = runtimes.count { it.reviewRequired || it.reviewEnqueued }
        return if (pendingCount > 0) {
            resourceProvider.getString(
                R.string.practice_shadowing_pending_review_format,
                pendingCount
            )
        } else {
            ""
        }
    }

    private fun buildReviewTagText(runtime: WordRuntime): String {
        return when {
            runtime.isCurrentReviewRound -> {
                resourceProvider.getString(R.string.practice_shadowing_reviewing_tag)
            }

            runtime.reviewRequired -> {
                resourceProvider.getString(R.string.practice_shadowing_review_flagged_tag)
            }

            else -> ""
        }
    }

    private fun buildPhonetic(word: Word): String {
        val phonetic = word.phoneticUS?.takeIf { it.isNotBlank() }
            ?: word.phoneticUK?.takeIf { it.isNotBlank() }
            ?: resourceProvider.getString(R.string.practice_shadowing_empty_phonetic)
        return resourceProvider.getString(R.string.practice_shadowing_phonetic_format, phonetic)
    }

    private fun buildMeaning(definitions: List<WordDefinitions>): String {
        val definition = definitions.firstOrNull()
        return if (definition == null) {
            resourceProvider.getString(R.string.practice_shadowing_empty_meaning)
        } else {
            resourceProvider.getString(
                R.string.practice_shadowing_meaning_format,
                definition.partOfSpeech.abbr,
                definition.meaningChinese
            )
        }
    }

    private fun buildFeedback(
        word: String,
        latestAttempt: ShadowingAttemptUi?
    ): Pair<String, String> {
        if (latestAttempt == null) {
            return resourceProvider.getString(R.string.practice_shadowing_feedback_waiting_title) to
                resourceProvider.getString(R.string.practice_shadowing_feedback_waiting_body)
        }
        val totalScore = latestAttempt.totalScore
        if (totalScore == null) {
            return resourceProvider.getString(R.string.practice_shadowing_feedback_retry_title) to
                latestAttempt.guidanceText
        }
        val titleRes = when {
            totalScore >= 90 -> R.string.practice_shadowing_feedback_excellent_title
            totalScore >= PASS_SCORE -> R.string.practice_shadowing_feedback_good_title
            else -> R.string.practice_shadowing_feedback_retry_title
        }
        val guidance = buildList {
            add(latestAttempt.guidanceText)
            val recognized = latestAttempt.recognizedText.trim()
            if (
                recognized.isNotBlank() &&
                recognized.lowercase(Locale.US) != word.lowercase(Locale.US)
            ) {
                add(
                    resourceProvider.getString(
                        R.string.practice_shadowing_feedback_recognized_mismatch,
                        recognized
                    )
                )
            }
        }.distinct().joinToString(separator = "\n")
        return resourceProvider.getString(titleRes) to guidance
    }

    private fun buildStatus(
        stage: ShadowingStage,
        runtime: WordRuntime
    ): Pair<String, String> {
        return when (stage) {
            ShadowingStage.WAITING -> {
                val subtitleRes = if (runtime.attempts.isEmpty()) {
                    R.string.practice_shadowing_status_waiting_subtitle
                } else {
                    R.string.practice_shadowing_status_retry_subtitle
                }
                resourceProvider.getString(R.string.practice_shadowing_status_waiting_title) to
                    resourceProvider.getString(subtitleRes)
            }

            ShadowingStage.RECORDING -> {
                resourceProvider.getString(R.string.practice_shadowing_status_recording_title) to
                    resourceProvider.getString(R.string.practice_shadowing_status_recording_subtitle)
            }

            ShadowingStage.EVALUATING -> {
                resourceProvider.getString(R.string.practice_shadowing_status_evaluating_title) to
                    resourceProvider.getString(R.string.practice_shadowing_status_evaluating_subtitle)
            }

            ShadowingStage.FEEDBACK -> {
                resourceProvider.getString(R.string.practice_shadowing_status_feedback_title) to
                    summaryText(buildSummary())
            }

            ShadowingStage.COMPLETED -> {
                resourceProvider.getString(R.string.practice_shadowing_finish_title) to
                    resourceProvider.getString(R.string.practice_shadowing_finish_subtitle)
            }
        }
    }

    private fun summaryText(summary: PracticeSessionSummary): String {
        return resourceProvider.getString(
            R.string.practice_shadowing_summary_format,
            summary.completedCount,
            summary.questionCount,
            summary.submitCount,
            summary.correctCount
        )
    }

    private fun AttemptRecord.toUi(
        index: Int,
        word: String,
        resourceProvider: ResourceProvider
    ): ShadowingAttemptUi {
        val title = resourceProvider.getString(
            R.string.practice_shadowing_history_title_format,
            index + 1
        )
        val evaluation = evaluation
        val scoreText = if (evaluation == null) {
            resourceProvider.getString(R.string.practice_shadowing_history_no_score)
        } else {
            resourceProvider.getString(
                R.string.practice_shadowing_score_brief_format,
                evaluation.totalScore
            )
        }
        val recognizedText = when {
            evaluation?.recognizedText?.isNotBlank() == true -> evaluation.recognizedText
            errorMessage.isNotBlank() -> errorMessage
            else -> word
        }
        val guidanceText = buildAttemptGuidance(
            result = evaluation,
            fallback = errorMessage,
            resourceProvider = resourceProvider
        )
        val intonationScore = evaluation?.intonationScore
        val stressScore = evaluation?.stressScore
        val speedScore = evaluation?.speedScore
        val scoreBreakdownText = if (
            intonationScore != null &&
            stressScore != null &&
            speedScore != null
        ) {
            resourceProvider.getString(
                R.string.practice_shadowing_score_breakdown_format,
                intonationScore,
                stressScore,
                speedScore
            )
        } else {
            ""
        }
        val audioIssueText = buildAudioIssueText(
            result = evaluation,
            resourceProvider = resourceProvider
        )
        val detailSourceNote = if (
            evaluation?.detailSourceNote?.isNotBlank() == true
        ) {
            evaluation.detailSourceNote.orEmpty()
        } else if (
            evaluation != null &&
            evaluation.analysisSource != ShadowingAnalysisSource.PROVIDER_ONLY
        ) {
            resourceProvider.getString(R.string.practice_shadowing_detail_note_placeholder)
        } else {
            ""
        }
        return ShadowingAttemptUi(
            attemptIndex = index + 1,
            title = title,
            scoreText = scoreText,
            recognizedText = recognizedText,
            guidanceText = guidanceText,
            audioFilePath = audioFilePath,
            waveformSamples = waveformSamples,
            totalScore = evaluation?.totalScore,
            pronunciationScore = evaluation?.pronunciationScore,
            fluencyScore = evaluation?.fluencyScore,
            intonationScore = evaluation?.intonationScore,
            stressScore = evaluation?.stressScore,
            speedScore = evaluation?.speedScore,
            scoreBreakdownText = scoreBreakdownText,
            audioIssueText = audioIssueText,
            detailSourceNote = detailSourceNote
        )
    }

    companion object {
        private const val PASS_SCORE = 80
        private const val MAX_AUTO_REVIEW_ROUNDS = 2
    }
}

internal fun nextShadowingPracticeRequestToken(currentToken: Int): Int {
    return currentToken + 1
}

private fun buildAttemptGuidance(
    result: ShadowingEvaluationResult?,
    fallback: String,
    resourceProvider: ResourceProvider
): String {
    if (result == null) {
        return fallback.ifBlank {
            resourceProvider.getString(R.string.practice_shadowing_scoring_unavailable)
        }
    }
    result.guidanceText?.takeIf { it.isNotBlank() }?.let { return it }
    return when {
        result.totalScore >= 90 -> {
            resourceProvider.getString(R.string.practice_shadowing_guidance_excellent)
        }

        result.pronunciationScore < 70 && result.fluencyScore < 70 -> {
            resourceProvider.getString(R.string.practice_shadowing_guidance_pron_fluency)
        }

        result.pronunciationScore < 70 -> {
            resourceProvider.getString(R.string.practice_shadowing_guidance_pronunciation)
        }

        result.fluencyScore < 70 -> {
            resourceProvider.getString(R.string.practice_shadowing_guidance_fluency)
        }

        else -> {
            resourceProvider.getString(R.string.practice_shadowing_guidance_good)
        }
    }
}


private fun shouldScheduleReview(
    evaluation: ShadowingEvaluationResult?,
    errorMessage: String
): Boolean {
    if (evaluation == null) return errorMessage.isNotBlank()
    return evaluation.totalScore < 80 ||
        evaluation.audioIssues.any { issue ->
            issue.type == ShadowingAudioIssueType.MOSTLY_SILENT ||
                issue.type == ShadowingAudioIssueType.TOO_FAST
        }
}

private fun buildAudioIssueText(
    result: ShadowingEvaluationResult?,
    resourceProvider: ResourceProvider
): String {
    if (result == null || result.audioIssues.isEmpty()) return ""
    val messages = result.audioIssues.map { issue ->
        issue.message?.takeIf { it.isNotBlank() } ?: when (issue.type) {
            ShadowingAudioIssueType.LOW_VOLUME -> {
                resourceProvider.getString(R.string.practice_shadowing_issue_low_volume)
            }

            ShadowingAudioIssueType.MOSTLY_SILENT -> {
                resourceProvider.getString(R.string.practice_shadowing_issue_mostly_silent)
            }

            ShadowingAudioIssueType.TOO_FAST -> {
                resourceProvider.getString(R.string.practice_shadowing_issue_too_fast)
            }

            ShadowingAudioIssueType.TOO_SLOW -> {
                resourceProvider.getString(R.string.practice_shadowing_issue_too_slow)
            }

            ShadowingAudioIssueType.ENVIRONMENT_NOISE -> {
                resourceProvider.getString(R.string.practice_shadowing_issue_environment_noise)
            }
        }
    }.distinct()
    return if (messages.isEmpty()) {
        ""
    } else {
        resourceProvider.getString(
            R.string.practice_shadowing_detected_issues_format,
            messages.joinToString(separator = "; ")
        )
    }
}
