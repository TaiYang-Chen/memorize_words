package com.chen.memorizewords.domain.study.model.learning

import com.chen.memorizewords.domain.wordbook.model.learning.LearningTestMode
import java.util.ArrayDeque

class LearningSessionEngine(
    wordIds: List<Long>,
    private val wrongTrackMasteryTarget: Int = DEFAULT_WRONG_TRACK_MASTERY_TARGET
) {

    data class SessionSnapshot(
        val currentWordId: Long?,
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
        val completedWordId: Long? = null
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

    private val originalWordIds = wordIds.distinct()
    private val progressByWordId = originalWordIds.associateWith { WordProgress() }.toMutableMap()
    private val newQueue = ArrayDeque<Long>().apply {
        originalWordIds.forEach { addLast(it) }
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
        var completedWordId: Long? = null

        when {
            isCorrect && progress.status == WordSessionStatus.UNSEEN -> {
                progress.consecutiveCorrect = 1
                completedWordId = completeWord(wordId)
            }

            !isCorrect && progress.status == WordSessionStatus.UNSEEN -> {
                progress.status = WordSessionStatus.WRONG_TRACK
                progress.consecutiveCorrect = 0
                progress.testMode = LearningTestMode.MEANING_CHOICE
                enqueueWrongWord(wordId)
            }

            isCorrect && progress.status == WordSessionStatus.WRONG_TRACK -> {
                progress.consecutiveCorrect += 1
                if (progress.consecutiveCorrect >= wrongTrackMasteryTarget) {
                    completedWordId = completeWord(wordId)
                } else {
                    enqueueWrongWord(wordId)
                }
            }

            !isCorrect && progress.status == WordSessionStatus.WRONG_TRACK -> {
                progress.consecutiveCorrect = 0
                progress.testMode = LearningTestMode.MEANING_CHOICE
                enqueueWrongWord(wordId)
            }
        }

        currentWordCompleted = completedWordId != null
        return SessionResult(
            snapshot = buildSnapshot(),
            completedWordId = completedWordId
        )
    }

    fun markCurrentWordMastered(): SessionResult {
        val wordId = currentWordId ?: return SessionResult(buildSnapshot())
        val progress = progressByWordId.getValue(wordId)
        if (progress.status == WordSessionStatus.MASTERED) {
            currentWordCompleted = true
            currentAnswered = true
            return SessionResult(buildSnapshot())
        }

        progress.consecutiveCorrect = maxOf(progress.consecutiveCorrect, 1)
        currentAnswered = true
        val completedWordId = completeWord(wordId)
        return SessionResult(
            snapshot = buildSnapshot(),
            completedWordId = completedWordId
        )
    }

    fun moveToNext(): SessionSnapshot {
        selectNextCard()
        return buildSnapshot()
    }

    private fun selectNextCard() {
        while (true) {
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

            if (!isDisplayableWord(nextWordId)) {
                continue
            }

            currentWordId = nextWordId
            currentPresentedMode = progressByWordId.getValue(nextWordId).testMode
            lastPresentedQueueType = nextType
            currentAnswered = false
            currentWordCompleted = false
            questionToken += 1
            return
        }
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

    private fun completeWord(wordId: Long): Long? {
        val progress = progressByWordId.getValue(wordId)
        progress.status = WordSessionStatus.MASTERED
        removePendingWord(wordId)
        val isNewCompletion = completedWordIds.add(wordId)
        currentWordCompleted = true
        return if (isNewCompletion) wordId else null
    }

    private fun enqueueWrongWord(wordId: Long) {
        val progress = progressByWordId[wordId] ?: return
        if (progress.status != WordSessionStatus.WRONG_TRACK) return
        if (!wrongQueue.contains(wordId)) {
            wrongQueue.addLast(wordId)
        }
    }

    private fun removePendingWord(wordId: Long) {
        removeAll(newQueue, wordId)
        removeAll(wrongQueue, wordId)
    }

    private fun removeAll(queue: ArrayDeque<Long>, wordId: Long) {
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            if (iterator.next() == wordId) {
                iterator.remove()
            }
        }
    }

    private fun isDisplayableWord(wordId: Long): Boolean {
        return progressByWordId[wordId]?.status != WordSessionStatus.MASTERED
    }

    private fun buildSnapshot(): SessionSnapshot {
        val progress = currentWordId?.let(progressByWordId::get)
        return SessionSnapshot(
            currentWordId = currentWordId,
            currentTestMode = if (currentWordId != null) currentPresentedMode else LearningTestMode.MEANING_CHOICE,
            isWrongTrackWord = progress?.status == WordSessionStatus.WRONG_TRACK,
            isAnswered = currentAnswered,
            isCurrentWordCompleted = currentWordCompleted,
            learnedWordsCount = completedWordIds.size,
            totalWordsCount = originalWordIds.size,
            answeredCount = answeredCount,
            correctCount = correctCount,
            wrongCount = wrongCount,
            isFinished = completedWordIds.size >= originalWordIds.size,
            questionToken = questionToken
        )
    }

    companion object {
        const val DEFAULT_WRONG_TRACK_MASTERY_TARGET: Int = 4
    }
}
