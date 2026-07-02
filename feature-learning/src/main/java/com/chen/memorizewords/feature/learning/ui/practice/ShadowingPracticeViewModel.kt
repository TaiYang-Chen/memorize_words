package com.chen.memorizewords.feature.learning.ui.practice

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.core.ui.vm.UiEffect
import com.chen.memorizewords.domain.practice.PracticeKind
import com.chen.memorizewords.domain.practice.PracticeReportRepository
import com.chen.memorizewords.domain.practice.PracticeSessionReportRecord
import com.chen.memorizewords.domain.practice.PracticeSessionReportTracker
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.word.model.word.WordDefinitions
import com.chen.memorizewords.domain.practice.PracticeWordProvider
import com.chen.memorizewords.domain.practice.ShadowingPracticeAttemptOutcome
import com.chen.memorizewords.domain.practice.ShadowingPracticeSessionPolicy
import com.chen.memorizewords.domain.practice.usecase.EvaluateShadowingUseCase
import com.chen.memorizewords.domain.practice.usecase.SynthesizeSpeechUseCase
import com.chen.memorizewords.domain.word.usecase.GetWordDefinitionsUseCase
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.domain.practice.speech.ShadowingAnalysisSource
import com.chen.memorizewords.domain.practice.speech.ShadowingAudioIssueType
import com.chen.memorizewords.domain.practice.speech.ShadowingEvaluationResult
import com.chen.memorizewords.domain.practice.speech.SpeechAudioInput
import com.chen.memorizewords.domain.practice.speech.ShadowingRecordingMetadata
import com.chen.memorizewords.domain.practice.speech.SpeechAudioSuccess
import com.chen.memorizewords.domain.practice.speech.SpeechFailureResult
import com.chen.memorizewords.domain.practice.speech.SpeechTask
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
    val attemptId: String,
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
    val accuracyScore: Int? = null,
    val standardScore: Int? = null,
    val scoreBreakdownText: String = "",
    val audioIssueText: String = "",
    val detailSourceNote: String = "",
    val weakPointText: String = "",
    val errorMessage: String = "",
    val hasEvaluation: Boolean = false,
    val isSelected: Boolean = false,
    val isEvaluating: Boolean = false
)

data class ShadowingPracticeDoneEffect(
    val questionCount: Int,
    val completedCount: Int,
    val correctCount: Int,
    val submitCount: Int
) : UiEffect

@HiltViewModel
class ShadowingPracticeViewModel @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val synthesizeSpeech: SynthesizeSpeechUseCase,
    private val evaluateShadowing: EvaluateShadowingUseCase,
    private val wordProvider: PracticeWordProvider,
    private val getWordDefinitions: GetWordDefinitionsUseCase,
    private val practiceReportRepository: PracticeReportRepository
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
        val canEvaluateLatestAttempt: Boolean = false,
        val isEvaluatingLatestAttempt: Boolean = false,
        val autoPlaySequenceId: Int = 0,
        val summary: PracticeSessionSummary = PracticeSessionSummary()
    )

    private data class AttemptRecord(
        val attemptId: String,
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
        var selectedAttemptId: String? = null
    )

    private val _uiState = MutableStateFlow(ShadowingUiState())
    val uiState: StateFlow<ShadowingUiState> = _uiState.asStateFlow()

    private val _sessionWordIds = MutableStateFlow<List<Long>>(emptyList())
    val sessionWordIds: StateFlow<List<Long>> = _sessionWordIds.asStateFlow()

    private var loadKey: String? = null
    private var runtimes: List<WordRuntime> = emptyList()
    private val sessionPolicy = ShadowingPracticeSessionPolicy()
    private val reportTracker = PracticeSessionReportTracker()
    private var currentRuntimeIndex: Int? = null
    private var renderRequestToken: Int = 0
    private var evaluateRequestToken: Int = 0
    private var nextAttemptSequence: Long = 0L
    private var evaluatingAttemptId: String? = null
    private var sessionId: String = ""
    private var reportSaved: Boolean = false
    private var doneEffectEmitted: Boolean = false

    fun loadWithSelection(selectedIds: LongArray?, randomCount: Int) {
        val newLoadKey = buildPracticeSelectionKey(selectedIds, randomCount)
        if (loadKey == newLoadKey) return
        loadKey = newLoadKey
        loadWords(selectedIds, randomCount)
    }

    fun onBackClick() {
        back()
    }

    fun onRecordingStarted() {
        val runtime = currentRuntime() ?: return
        if (_uiState.value.loading || _uiState.value.isCompleted) return
        evaluatingAttemptId = null
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
        val attemptId = nextAttemptId(runtime.word.id)
        val attempt = AttemptRecord(
            attemptId = attemptId,
            audioFilePath = audioFilePath,
            waveformSamples = waveformSamples.ifEmpty { listOf(16, 24, 18, 30, 20, 26, 14, 22) },
            durationMs = durationMs
        )
        runtime.attempts += attempt
        runtime.selectedAttemptId = attemptId
        val autoPlaySequenceId = _uiState.value.autoPlaySequenceId + 1
        _uiState.value = buildUiState(
            runtime = runtime,
            stage = ShadowingStage.FEEDBACK,
            loading = false,
            autoPlaySequenceId = autoPlaySequenceId
        )
    }

    fun evaluateLatestAttempt() {
        val state = _uiState.value
        if (
            state.loading ||
            state.stage == ShadowingStage.RECORDING ||
            state.isEvaluatingLatestAttempt
        ) {
            return
        }
        val runtimeIndex = currentRuntimeIndex ?: return
        val runtime = runtimes.getOrNull(runtimeIndex) ?: return
        val selectedAttemptId = runtime.selectedAttemptId ?: runtime.attempts.lastOrNull()?.attemptId ?: return
        val attemptIndex = runtime.attempts.indexOfFirst { it.attemptId == selectedAttemptId }
        if (attemptIndex < 0) return
        val attempt = runtime.attempts.getOrNull(attemptIndex) ?: return
        if (attempt.evaluation != null) return
        val localQualityMessage = localQualityMessage(attempt)
        if (localQualityMessage != null) {
            runtime.attempts[attemptIndex] = attempt.copy(errorMessage = localQualityMessage)
            _uiState.value = buildUiState(
                runtime = runtime,
                stage = state.stage,
                loading = false,
                autoPlaySequenceId = state.autoPlaySequenceId
            )
            return
        }
        evaluatingAttemptId = attempt.attemptId
        _uiState.value = buildUiState(
            runtime = runtime,
            stage = state.stage,
            loading = false,
            autoPlaySequenceId = state.autoPlaySequenceId,
            isEvaluatingLatestAttempt = true
        )
        evaluateAttempt(runtimeIndex = runtimeIndex, attemptId = attempt.attemptId)
    }

    private fun localQualityMessage(attempt: AttemptRecord): String? {
        if (attempt.durationMs in 1 until MIN_EVALUATE_DURATION_MS) {
            return resourceProvider.getString(R.string.practice_shadowing_local_issue_too_short)
        }
        val samples = attempt.waveformSamples
        if (samples.isEmpty()) return null
        val averageVolume = samples.map { it.coerceIn(0, 100) }.average()
        val speechRatio = samples.count { it >= MIN_SPEECH_SAMPLE_VOLUME } * 100 / samples.size
        return when {
            averageVolume < MIN_AVERAGE_VOLUME || speechRatio < MIN_SPEECH_RATIO -> {
                resourceProvider.getString(R.string.practice_shadowing_local_issue_low_volume)
            }

            averageVolume > MAX_AVERAGE_VOLUME -> {
                resourceProvider.getString(R.string.practice_shadowing_local_issue_clipping)
            }

            else -> null
        }
    }

    fun selectAttempt(attemptId: String) {
        val runtime = currentRuntime() ?: return
        if (runtime.attempts.none { it.attemptId == attemptId }) return
        runtime.selectedAttemptId = attemptId
        _uiState.value = buildUiState(
            runtime = runtime,
            stage = _uiState.value.stage,
            loading = false,
            autoPlaySequenceId = _uiState.value.autoPlaySequenceId,
            isEvaluatingLatestAttempt = evaluatingAttemptId == attemptId
        )
    }

    fun retryCurrentWord() {
        val runtime = currentRuntime() ?: return
        if (
            _uiState.value.loading ||
            _uiState.value.stage == ShadowingStage.RECORDING
        ) {
            return
        }
        _uiState.value = buildUiState(
            runtime = runtime,
            stage = ShadowingStage.WAITING,
            loading = false,
            autoPlaySequenceId = _uiState.value.autoPlaySequenceId
        )
    }

    fun nextWord() {
        if (
            _uiState.value.loading ||
            _uiState.value.stage == ShadowingStage.RECORDING
        ) {
            return
        }
        if (_uiState.value.isCompleted) {
            finish()
            return
        }
        val runtimeIndex = currentRuntimeIndex ?: return
        runtimes.getOrNull(runtimeIndex) ?: return
        openNextWord()
    }

    private fun evaluateAttempt(runtimeIndex: Int, attemptId: String) {
        val runtime = runtimes.getOrNull(runtimeIndex) ?: return
        val attemptIndex = runtime.attempts.indexOfFirst { it.attemptId == attemptId }
        if (attemptIndex < 0) return
        val wordText = runtime.word.word.trim()
        if (wordText.isBlank()) {
            if (evaluatingAttemptId == attemptId) {
                evaluatingAttemptId = null
            }
            _uiState.value = buildUiState(
                runtime = runtime,
                stage = _uiState.value.stage,
                loading = false,
                autoPlaySequenceId = _uiState.value.autoPlaySequenceId,
                isEvaluatingLatestAttempt = false
            )
            return
        }
        val requestToken = nextShadowingPracticeRequestToken(evaluateRequestToken)
        evaluateRequestToken = requestToken
        val audioFilePath = runtime.attempts[attemptIndex].audioFilePath
        val durationMs = runtime.attempts[attemptIndex].durationMs
        val waveformSamples = runtime.attempts[attemptIndex].waveformSamples
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                evaluateShadowing(
                    SpeechTask.EvaluateShadowing(
                        referenceText = wordText,
                        audioInput = SpeechAudioInput.FileInput(
                            audioFilePath
                        ),
                        recordingMetadata = ShadowingRecordingMetadata(
                            durationMs = durationMs,
                            waveformSamples = waveformSamples
                        )
                    )
                )
            }
            val activeRuntimeIndex = currentRuntimeIndex
            if (
                evaluatingAttemptId != attemptId ||
                evaluateRequestToken != requestToken
            ) {
                return@launch
            }
            val latestRuntime = runtimes.getOrNull(runtimeIndex) ?: return@launch
            val latestAttemptIndex = latestRuntime.attempts.indexOfFirst { it.attemptId == attemptId }
            val oldAttempt = latestRuntime.attempts.getOrNull(latestAttemptIndex) ?: return@launch
            val updatedAttempt = when (result) {
                is ShadowingEvaluationResult -> oldAttempt.copy(
                    evaluation = result,
                    errorMessage = ""
                )

                is SpeechFailureResult -> oldAttempt.copy(
                    evaluation = null,
                    errorMessage = normalizeShadowingFailureMessage(
                        message = result.message,
                        resourceProvider = resourceProvider
                    )
                )

                else -> oldAttempt
            }
            latestRuntime.attempts[latestAttemptIndex] = updatedAttempt
            recordAttempt(
                word = latestRuntime.word,
                attemptIndex = latestAttemptIndex,
                attempt = updatedAttempt
            )
            evaluatingAttemptId = null
            if (activeRuntimeIndex != runtimeIndex) {
                return@launch
            }
            _uiState.value = buildUiState(
                runtime = latestRuntime,
                stage = _uiState.value.stage,
                loading = false,
                autoPlaySequenceId = _uiState.value.autoPlaySequenceId,
                isEvaluatingLatestAttempt = false
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
            reportTracker.clear()
            reportSaved = false
            sessionId = "shadowing:${System.currentTimeMillis()}:${loadKey.orEmpty()}"
            currentRuntimeIndex = null
            renderRequestToken = nextShadowingPracticeRequestToken(renderRequestToken)
            evaluateRequestToken = nextShadowingPracticeRequestToken(evaluateRequestToken)
            evaluatingAttemptId = null
            nextAttemptSequence = 0L
            doneEffectEmitted = false
            openNextWord()
        }
    }

    private fun openNextWord() {
        val runtimeIndex = selectNextWordIndex() ?: run {
            renderCompletedState()
            return
        }
        currentRuntimeIndex = runtimeIndex
        val runtime = runtimes.getOrNull(runtimeIndex) ?: run {
            renderCompletedState()
            return
        }
        renderCurrentWord(runtimeIndex)
    }

    private fun nextAttemptId(wordId: Long): String {
        nextAttemptSequence += 1
        return "shadowing_${wordId}_${System.currentTimeMillis()}_$nextAttemptSequence"
    }

    private fun selectNextWordIndex(): Int? {
        if (runtimes.isEmpty()) return null
        val currentIndex = currentRuntimeIndex ?: return 0
        return (currentIndex + 1).takeIf { it < runtimes.size }
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
        persistPracticeReportIfNeeded()
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
            pendingReviewText = "",
            reviewTagText = "",
            isCompleted = true,
            nextActionText = resourceProvider.getString(R.string.practice_shadowing_finish_action),
            autoPlaySequenceId = _uiState.value.autoPlaySequenceId,
            summary = summary
        )
        if (!doneEffectEmitted) {
            doneEffectEmitted = true
            emitEffect(
                ShadowingPracticeDoneEffect(
                    questionCount = summary.questionCount,
                    completedCount = summary.completedCount,
                    correctCount = summary.correctCount,
                    submitCount = summary.submitCount
                )
            )
        }
    }

    private fun buildUiState(
        runtime: WordRuntime,
        stage: ShadowingStage,
        loading: Boolean,
        autoPlaySequenceId: Int,
        isEvaluatingLatestAttempt: Boolean = false
    ): ShadowingUiState {
        val attempts = runtime.attempts.mapIndexed { index, attempt ->
            attempt.toUi(
                index = index,
                word = runtime.word.word,
                resourceProvider = resourceProvider,
                isSelected = runtime.selectedAttemptId == attempt.attemptId,
                isEvaluating = evaluatingAttemptId == attempt.attemptId
            )
        }
        val selectedAttempt = attempts.firstOrNull { it.isSelected } ?: attempts.lastOrNull()
        if (selectedAttempt != null && runtime.selectedAttemptId != selectedAttempt.attemptId) {
            runtime.selectedAttemptId = selectedAttempt.attemptId
        }
        val summary = buildSummary()
        val nextActionText = when {
            stage == ShadowingStage.COMPLETED -> {
                resourceProvider.getString(R.string.practice_shadowing_finish_action)
            }

            isFinalAction(runtime) -> resourceProvider.getString(R.string.practice_shadowing_finish_action)
            else -> resourceProvider.getString(R.string.practice_next_word)
        }
        val phonetic = buildPhonetic(runtime.word)
        val meaning = buildMeaning(runtime.definitions)
        val feedback = buildFeedback(
            word = runtime.word.word,
            latestAttempt = selectedAttempt,
            isEvaluatingLatestAttempt = selectedAttempt?.isEvaluating == true || isEvaluatingLatestAttempt
        )
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
            latestAttempt = selectedAttempt,
            attemptHistory = attempts,
            feedbackTitle = feedback.first,
            feedbackMessage = feedback.second,
            statusTitle = statusPair.first,
            statusSubtitle = statusPair.second,
            summaryText = summaryText(summary),
            pendingReviewText = "",
            isInReview = false,
            reviewTagText = "",
            scoreBreakdownText = selectedAttempt?.scoreBreakdownText.orEmpty(),
            audioIssueText = selectedAttempt?.audioIssueText.orEmpty(),
            detailSourceNote = selectedAttempt?.detailSourceNote.orEmpty(),
            isCompleted = false,
            nextActionText = nextActionText,
            canEvaluateLatestAttempt = selectedAttempt != null &&
                !selectedAttempt.hasEvaluation &&
                stage != ShadowingStage.RECORDING &&
                selectedAttempt.isEvaluating.not(),
            isEvaluatingLatestAttempt = selectedAttempt?.isEvaluating == true || isEvaluatingLatestAttempt,
            autoPlaySequenceId = autoPlaySequenceId,
            summary = summary
        )
    }

    private fun buildSummary(): PracticeSessionSummary {
        val summary = sessionPolicy.buildSummary(
            totalQuestionCount = runtimes.size,
            attemptsByWordId = runtimes.associate { runtime ->
                runtime.word.id to runtime.attempts.mapIndexed { index, attempt ->
                    attempt.toOutcome(
                        wordId = runtime.word.id,
                        wordText = runtime.word.word,
                        attemptIndex = index
                    )
                }
            }
        )
        return PracticeSessionSummary(
            questionCount = summary.questionCount,
            completedCount = summary.completedCount,
            correctCount = summary.correctCount,
            submitCount = summary.submitCount
        )
    }

    private fun currentRuntime(): WordRuntime? {
        return currentRuntimeIndex?.let { runtimes.getOrNull(it) }
    }

    private fun recordAttempt(
        word: Word,
        attemptIndex: Int,
        attempt: AttemptRecord
    ) {
        val answerRecord = sessionPolicy.toAnswerRecord(
            attempt.toOutcome(
                wordId = word.id,
                wordText = word.word,
                attemptIndex = attemptIndex
            )
        ) ?: return
        reportTracker.record(answerRecord)
    }

    private fun persistPracticeReportIfNeeded() {
        if (reportSaved) return
        val report = reportTracker.buildReport(totalQuestionCount = runtimes.size)
        if (report.answeredCount <= 0) return
        reportSaved = true
        viewModelScope.launch {
            practiceReportRepository.save(
                PracticeSessionReportRecord(
                    sessionId = sessionId.ifBlank {
                        "shadowing:${System.currentTimeMillis()}:${loadKey.orEmpty()}"
                    },
                    kind = PracticeKind.SHADOWING,
                    report = report,
                    completedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    private fun isFinalAction(runtime: WordRuntime): Boolean {
        val runtimeIndex = runtimes.indexOf(runtime)
        return runtime.attempts.isNotEmpty() && runtimeIndex == runtimes.lastIndex
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
        latestAttempt: ShadowingAttemptUi?,
        isEvaluatingLatestAttempt: Boolean
    ): Pair<String, String> {
        if (isEvaluatingLatestAttempt) {
            return resourceProvider.getString(R.string.practice_shadowing_status_evaluating_title) to
                resourceProvider.getString(R.string.practice_shadowing_status_evaluating_subtitle)
        }
        if (latestAttempt == null) {
            return resourceProvider.getString(R.string.practice_shadowing_feedback_waiting_title) to
                resourceProvider.getString(R.string.practice_shadowing_feedback_waiting_body)
        }
        val totalScore = latestAttempt.totalScore
        if (totalScore == null) {
            val body = if (latestAttempt.errorMessage.isNotBlank()) {
                latestAttempt.errorMessage
            } else {
                resourceProvider.getString(R.string.practice_shadowing_feedback_ready_body)
            }
            return resourceProvider.getString(R.string.practice_shadowing_feedback_ready_title) to body
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
                val latestAttempt = runtime.attempts.lastOrNull()
                if (latestAttempt != null && latestAttempt.evaluation == null) {
                    resourceProvider.getString(R.string.practice_shadowing_feedback_ready_title) to
                        resourceProvider.getString(R.string.practice_shadowing_feedback_ready_body)
                } else {
                    resourceProvider.getString(R.string.practice_shadowing_status_feedback_title) to
                        summaryText(buildSummary())
                }
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
        resourceProvider: ResourceProvider,
        isSelected: Boolean,
        isEvaluating: Boolean
    ): ShadowingAttemptUi {
        val title = resourceProvider.getString(
            R.string.practice_shadowing_history_title_format,
            index + 1
        )
        val evaluation = evaluation
        val scoreText = if (evaluation == null) {
            if (isEvaluating) {
                resourceProvider.getString(R.string.practice_shadowing_history_evaluating)
            } else {
                resourceProvider.getString(R.string.practice_shadowing_history_no_score)
            }
        } else {
            resourceProvider.getString(
                R.string.practice_shadowing_score_brief_format,
                evaluation.totalScore
            )
        }
        val recognizedText = when {
            evaluation?.recognizedText?.isNotBlank() == true -> evaluation.recognizedText
            evaluation != null -> word
            else -> ""
        }
        val guidanceText = buildAttemptGuidance(
            result = evaluation,
            fallback = errorMessage,
            resourceProvider = resourceProvider
        )
        val intonationScore = evaluation?.intonationScore
        val stressScore = evaluation?.stressScore
        val speedScore = evaluation?.speedScore
        val scoreBreakdownText = buildScoreBreakdown(evaluation, resourceProvider)
        val audioIssueText = buildAudioIssueText(
            result = evaluation,
            resourceProvider = resourceProvider
        )
        val weakPointText = buildWeakPointText(evaluation, resourceProvider)
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
            attemptId = attemptId,
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
            accuracyScore = evaluation?.accuracyScore,
            standardScore = evaluation?.standardScore,
            scoreBreakdownText = scoreBreakdownText,
            audioIssueText = audioIssueText,
            detailSourceNote = detailSourceNote,
            weakPointText = weakPointText,
            errorMessage = errorMessage,
            hasEvaluation = evaluation != null,
            isSelected = isSelected,
            isEvaluating = isEvaluating
        )
    }

    private fun AttemptRecord.toOutcome(
        wordId: Long,
        wordText: String,
        attemptIndex: Int
    ): ShadowingPracticeAttemptOutcome {
        return ShadowingPracticeAttemptOutcome(
            wordId = wordId,
            wordText = wordText,
            attemptIndex = attemptIndex,
            evaluation = evaluation,
            errorMessage = errorMessage
        )
    }

    companion object {
        private const val PASS_SCORE = 80
        private const val MIN_EVALUATE_DURATION_MS = 450L
        private const val MIN_SPEECH_SAMPLE_VOLUME = 12
        private const val MIN_AVERAGE_VOLUME = 8.0
        private const val MIN_SPEECH_RATIO = 15
        private const val MAX_AVERAGE_VOLUME = 96.0
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

private fun buildScoreBreakdown(
    result: ShadowingEvaluationResult?,
    resourceProvider: ResourceProvider
): String {
    if (result == null) return ""
    val items = buildList {
        result.accuracyScore?.let {
            add(resourceProvider.getString(R.string.practice_shadowing_metric_accuracy, it))
        }
        result.standardScore?.let {
            add(resourceProvider.getString(R.string.practice_shadowing_metric_standard, it))
        }
        result.intonationScore?.let {
            add(resourceProvider.getString(R.string.practice_shadowing_metric_intonation, it))
        }
        result.speedScore?.let {
            add(resourceProvider.getString(R.string.practice_shadowing_metric_speed, it))
        }
    }
    return items.joinToString(separator = " · ")
}

private fun buildWeakPointText(
    result: ShadowingEvaluationResult?,
    resourceProvider: ResourceProvider
): String {
    if (result == null) return ""
    val weakPoints = (result.phoneDetails + result.syllableDetails + result.wordDetails)
        .filter { detail -> (detail.score ?: 100) < 75 && detail.text.isNotBlank() }
        .take(4)
        .map { detail ->
            val label = readablePronunciationUnit(detail.text)
            if (detail.score == null) label else "$label ${detail.score}分"
        }
        .distinct()
    if (weakPoints.isEmpty()) return ""
    return resourceProvider.getString(
        R.string.practice_shadowing_weak_points_format,
        weakPoints.joinToString(separator = " · ")
    )
}

private fun readablePronunciationUnit(rawText: String): String {
    val normalized = rawText.trim()
    if (normalized.isBlank()) return rawText
    val key = normalized.lowercase(Locale.US).replace(Regex("[^a-z]"), "")
    val phoneme = when (key) {
        "ih" -> "短元音 /ɪ/"
        "iy" -> "长元音 /iː/"
        "eh" -> "短元音 /e/"
        "ae" -> "短元音 /æ/"
        "ah" -> "弱读元音 /ə/"
        "er" -> "卷舌元音 /ɜː/"
        "aa" -> "后元音 /ɑː/"
        "ao" -> "后元音 /ɔː/"
        "uh" -> "短元音 /ʊ/"
        "uw" -> "长元音 /uː/"
        "ey" -> "双元音 /eɪ/"
        "ay" -> "双元音 /aɪ/"
        "aw" -> "双元音 /aʊ/"
        "ow" -> "双元音 /oʊ/"
        "oy" -> "双元音 /ɔɪ/"
        "th" -> "咬舌音 /θ/"
        "dh" -> "咬舌音 /ð/"
        "sh" -> "摩擦音 /ʃ/"
        "zh" -> "摩擦音 /ʒ/"
        "ch" -> "破擦音 /tʃ/"
        "jh" -> "破擦音 /dʒ/"
        "ng" -> "鼻音 /ŋ/"
        "r" -> "卷舌音 /r/"
        "l" -> "舌侧音 /l/"
        "t" -> "词尾 /t/"
        "d" -> "词尾 /d/"
        "s" -> "词尾 /s/"
        "z" -> "词尾 /z/"
        "p" -> "爆破音 /p/"
        "b" -> "爆破音 /b/"
        "k" -> "爆破音 /k/"
        "g" -> "爆破音 /g/"
        "f" -> "摩擦音 /f/"
        "v" -> "摩擦音 /v/"
        "m" -> "鼻音 /m/"
        "n" -> "鼻音 /n/"
        "w" -> "半元音 /w/"
        "y" -> "半元音 /j/"
        "hh" -> "气音 /h/"
        else -> null
    }
    return phoneme ?: normalized
}

private fun normalizeShadowingFailureMessage(
    message: String?,
    resourceProvider: ResourceProvider
): String {
    val raw = message?.trim().orEmpty()
    if (raw.isBlank()) {
        return resourceProvider.getString(R.string.practice_shadowing_scoring_unavailable)
    }
    val normalized = raw.lowercase(Locale.US)
    return when {
        "provider must be baidu" in normalized ||
            "word is required" in normalized ||
            "provider must be xunfei" in normalized ||
            "referencetext is required" in normalized -> {
            resourceProvider.getString(R.string.practice_shadowing_service_contract_mismatch)
        }

        else -> raw
    }
}
