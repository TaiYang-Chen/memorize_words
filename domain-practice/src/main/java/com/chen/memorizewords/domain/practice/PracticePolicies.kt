package com.chen.memorizewords.domain.practice
interface PracticeQuestionFactory {
    fun buildQuestions(kind: PracticeKind, words: List<PracticeWord>): List<PracticeQuestion>
}

class DefaultPracticeQuestionFactory(
    plugins: List<PracticePlugin> = listOf(
        MeaningChoicePracticePlugin(),
        SpellingPracticePlugin(),
        ShadowingPracticePlugin(),
        AudioLoopPracticePlugin(),
        ExamPracticePlugin()
    )
) : PracticeQuestionFactory {
    private val pluginsByKind = plugins.associateBy { plugin -> plugin.kind }

    override fun buildQuestions(kind: PracticeKind, words: List<PracticeWord>): List<PracticeQuestion> {
        val plugin = requireNotNull(pluginsByKind[kind]) {
            "No practice plugin registered for $kind"
        }
        return words.mapIndexed { index, word ->
            plugin.buildQuestion(word = word, index = index, allWords = words)
        }
    }
}

class PracticeReviewPolicy(
    private val reviewTarget: Int = DEFAULT_REVIEW_TARGET
) {
    fun shouldComplete(question: PracticeQuestion, history: List<PracticeAnswerRecord>): Boolean {
        val correctCount = history.count {
            it.questionId == question.id && it.status == PracticeAnswerStatus.CORRECT
        }
        return correctCount >= reviewTarget
    }

    fun shouldEnqueueAfterAnswer(
        question: PracticeQuestion,
        historyBeforeAnswer: List<PracticeAnswerRecord>,
        historyAfterAnswer: List<PracticeAnswerRecord>,
        status: PracticeAnswerStatus
    ): Boolean {
        if (status != PracticeAnswerStatus.CORRECT) return true

        val hadPriorMiss = historyBeforeAnswer.any {
            it.questionId == question.id && it.status != PracticeAnswerStatus.CORRECT
        }
        if (!hadPriorMiss) return false

        return !shouldComplete(question, historyAfterAnswer)
    }

    fun enqueueReview(
        queue: List<PracticeQuestion>,
        question: PracticeQuestion
    ): List<PracticeQuestion> {
        if (queue.any { it.id == question.id }) return queue
        return queue + question
    }

    private companion object {
        const val DEFAULT_REVIEW_TARGET = 3
    }
}

class PracticeReportPolicy {
    fun buildReport(totalQuestionCount: Int, history: List<PracticeAnswerRecord>): PracticeReport {
        val answered = history.count {
            it.status == PracticeAnswerStatus.CORRECT || it.status == PracticeAnswerStatus.WRONG
        }
        val correct = history.count { it.status == PracticeAnswerStatus.CORRECT }
        val wrong = history.count { it.status == PracticeAnswerStatus.WRONG }
        val skipped = history.count { it.status == PracticeAnswerStatus.SKIPPED }
        val revealed = history.count { it.status == PracticeAnswerStatus.REVEALED }
        val accuracy = if (answered == 0) {
            0
        } else {
            ((correct.toFloat() / answered.toFloat()) * 100f).toInt()
        }
        return PracticeReport(
            totalQuestionCount = totalQuestionCount,
            answeredCount = answered,
            correctCount = correct,
            wrongCount = wrong,
            skippedCount = skipped,
            revealedCount = revealed,
            accuracyPercent = accuracy
        )
    }
}
