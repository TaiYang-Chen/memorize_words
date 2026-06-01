package com.chen.memorizewords.domain.practice

class PracticeSessionReportTracker(
    private val reportPolicy: PracticeReportPolicy = PracticeReportPolicy()
) {
    private val answerHistory = mutableListOf<PracticeAnswerRecord>()

    fun clear() {
        answerHistory.clear()
    }

    fun nextOrdinal(): Int = answerHistory.size

    fun record(record: PracticeAnswerRecord) {
        answerHistory += record
    }

    fun buildReport(totalQuestionCount: Int): PracticeReport {
        return reportPolicy.buildReport(
            totalQuestionCount = totalQuestionCount,
            history = answerHistory
        )
    }
}
