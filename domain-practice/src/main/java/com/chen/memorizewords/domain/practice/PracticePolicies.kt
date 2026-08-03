package com.chen.memorizewords.domain.practice
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
