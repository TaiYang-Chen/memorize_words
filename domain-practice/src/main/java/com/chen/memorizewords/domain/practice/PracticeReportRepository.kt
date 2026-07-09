package com.chen.memorizewords.domain.practice
data class PracticeSessionReportRecord(
    val sessionId: String,
    val kind: PracticeKind,
    val report: PracticeReport,
    val completedAtMs: Long
)

interface PracticeReportRepository {
    suspend fun save(record: PracticeSessionReportRecord)
    suspend fun getLatest(kind: PracticeKind): PracticeSessionReportRecord?
    suspend fun getBySessionId(sessionId: String): PracticeSessionReportRecord?
}
