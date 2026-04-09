package com.chen.memorizewords.feature.learning.ui.learning

import com.chen.memorizewords.domain.model.learning.LearningTestMode
import com.chen.memorizewords.domain.model.learning.WordSessionStatus
import com.chen.memorizewords.domain.model.words.word.Word
import java.util.ArrayDeque

internal class LearningSessionCoordinator(
    words: List<Word>,
    private val wrongTrackMasteryTarget: Int = DEFAULT_WRONG_TRACK_MASTERY_TARGET
) {

    data class SessionSnapshot(
        val currentWord: Word?,
        val currentTestMode: LearningTestMode,
        val isWrongTrackWord: Boolean,
        val isAnswered: Boolean,
        val isCurrentWordCompleted: Boolean,
        val learnedWordsCount: Int,
        val totalWordsCount: Int,
        val answeredCount: Int,
        val correctCount: Int,
        val wrongCount: Int,
        val isFinished: Boolean,
        val questionToken: Int
    )

    data class SessionResult(
        val snapshot: SessionSnapshot,
        val newlyCompletedWord: Word? = null
    )

    private enum class QueueType {
        NEW,
        WRONG
    }

    private data class WordProgress(
        var status: WordSessionStatus = WordSessionStatus.UNSEEN,
        var consecutiveCorrect: Int = 0,
        var testMode: LearningTestMode = LearningTestMode.MEANING_CHOICE
    )

    private val originalWords = words.toList()
    private val wordsById = originalWords.associateBy { it.id }
    private val progressByWordId = originalWords.associate { it.id to WordProgress() }.toMutableMap()
    private val newQueue = ArrayDeque<Long>().apply {
        originalWords.forEach { addLast(it.id) }
    }
    private val wrongQueue = ArrayDeque<Long>()
    private val completedWordIds = linkedSetOf<Long>()

    private var currentWordId: Long? = null
    private var currentPresentedMode: LearningTestMode = LearningTestMode.MEANING_CHOICE
    private var lastPresentedQueueType: QueueType? = null
    private var currentAnswered: Boolean = false
    private var currentWordCompleted: Boolean = false
    private var questionToken: Int = 0
    private var answeredCount: Int = 0
    private var correctCount: Int = 0
    private var wrongCount: Int = 0

    init {
        selectNextCard()
    }

    fun snapshot(): SessionSnapshot = buildSnapshot()

    fun submitAnswer(isCorrect: Boolean): SessionResult {
        val wordId = currentWordId ?: return SessionResult(buildSnapshot())
        if (currentAnswered) return SessionResult(buildSnapshot())

        currentAnswered = true
        answeredCount += 1
        if (isCorrect) {
            correctCount += 1
        } else {
            wrongCount += 1
        }

        val progress = progressByWordId.getValue(wordId)
        val currentWord = wordsById.getValue(wordId)
        var newlyCompletedWord: Word? = null

        when {
            isCorrect && progress.status == WordSessionStatus.UNSEEN -> {
                progress.status = WordSessionStatus.MASTERED
                progress.consecutiveCorrect = 1
                newlyCompletedWord = completeWord(wordId, currentWord)
            }

            !isCorrect && progress.status == WordSessionStatus.UNSEEN -> {
                progress.status = WordSessionStatus.WRONG_TRACK
                progress.consecutiveCorrect = 0
                progress.testMode = LearningTestMode.LISTENING
                enqueueWrongWord(wordId)
            }

            isCorrect && progress.status == WordSessionStatus.WRONG_TRACK -> {
                progress.consecutiveCorrect += 1
                if (progress.consecutiveCorrect >= wrongTrackMasteryTarget) {
                    progress.status = WordSessionStatus.MASTERED
                    newlyCompletedWord = completeWord(wordId, currentWord)
                } else {
                    enqueueWrongWord(wordId)
                }
            }

            !isCorrect && progress.status == WordSessionStatus.WRONG_TRACK -> {
                progress.consecutiveCorrect = 0
                progress.testMode = upgradeMode(progress.testMode)
                enqueueWrongWord(wordId)
            }
        }

        currentWordCompleted = newlyCompletedWord != null
        return SessionResult(
            snapshot = buildSnapshot(),
            newlyCompletedWord = newlyCompletedWord
        )
    }

    fun markCurrentWordMastered(): SessionResult {
        val wordId = currentWordId ?: return SessionResult(buildSnapshot())
        val progress = progressByWordId.getValue(wordId)
        val currentWord = wordsById.getValue(wordId)
        if (progress.status == WordSessionStatus.MASTERED) {
            currentWordCompleted = true
            currentAnswered = true
            return SessionResult(buildSnapshot())
        }

        progress.status = WordSessionStatus.MASTERED
        progress.consecutiveCorrect = maxOf(progress.consecutiveCorrect, 1)
        currentWordCompleted = true
        currentAnswered = true
        val newlyCompletedWord = completeWord(wordId, currentWord)
        return SessionResult(
            snapshot = buildSnapshot(),
            newlyCompletedWord = newlyCompletedWord
        )
    }

    fun moveToNext(): SessionSnapshot {
        selectNextCard()
        return buildSnapshot()
    }

    private fun selectNextCard() {
        val nextType = resolveNextQueueType()
        if (nextType == null) {
            currentWordId = null
            currentPresentedMode = LearningTestMode.MEANING_CHOICE
            currentAnswered = false
            currentWordCompleted = false
            return
        }

        val nextWordId = when (nextType) {
            QueueType.NEW -> newQueue.removeFirst()
            QueueType.WRONG -> wrongQueue.removeFirst()
        }

        currentWordId = nextWordId
        currentPresentedMode = progressByWordId
            .getValue(nextWordId)
            .testMode
        lastPresentedQueueType = nextType
        currentAnswered = false
        currentWordCompleted = false
        questionToken += 1
    }

    private fun resolveNextQueueType(): QueueType? {
        val hasNew = newQueue.isNotEmpty()
        val hasWrong = wrongQueue.isNotEmpty()
        if (!hasNew && !hasWrong) return null
        return when {
            hasNew && hasWrong -> {
                if (lastPresentedQueueType == QueueType.NEW) QueueType.WRONG else QueueType.NEW
            }

            hasNew -> QueueType.NEW
            else -> QueueType.WRONG
        }
    }

    private fun completeWord(wordId: Long, word: Word): Word? {
        val isNewCompletion = completedWordIds.add(wordId)
        currentWordCompleted = true
        return if (isNewCompletion) word else null
    }

    private fun enqueueWrongWord(wordId: Long) {
        if (!wrongQueue.contains(wordId)) {
            wrongQueue.addLast(wordId)
        }
    }

    private fun buildSnapshot(): SessionSnapshot {
        val currentWord = currentWordId?.let(wordsById::get)
        val progress = currentWordId?.let(progressByWordId::get)
        return SessionSnapshot(
            currentWord = currentWord,
            currentTestMode = if (currentWord != null) currentPresentedMode else LearningTestMode.MEANING_CHOICE,
            isWrongTrackWord = progress?.status == WordSessionStatus.WRONG_TRACK,
            isAnswered = currentAnswered,
            isCurrentWordCompleted = currentWordCompleted,
            learnedWordsCount = completedWordIds.size,
            totalWordsCount = originalWords.size,
            answeredCount = answeredCount,
            correctCount = correctCount,
            wrongCount = wrongCount,
            isFinished = completedWordIds.size >= originalWords.size,
            questionToken = questionToken
        )
    }

    private fun upgradeMode(currentMode: LearningTestMode): LearningTestMode {
        return when (currentMode) {
            LearningTestMode.MEANING_CHOICE -> LearningTestMode.LISTENING
            LearningTestMode.LISTENING -> LearningTestMode.SPELLING
            LearningTestMode.SPELLING -> LearningTestMode.SPELLING
        }
    }

    companion object {
        const val DEFAULT_WRONG_TRACK_MASTERY_TARGET: Int = 4
    }
}
