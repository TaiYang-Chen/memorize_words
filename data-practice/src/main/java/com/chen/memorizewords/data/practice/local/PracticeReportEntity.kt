package com.chen.memorizewords.data.practice.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import com.chen.memorizewords.domain.practice.PracticeKind
import com.chen.memorizewords.domain.practice.PracticeReport
import com.chen.memorizewords.domain.practice.PracticeSessionReportRecord

@Entity(tableName = "practice_report")
data class PracticeReportEntity(
    @PrimaryKey val sessionId: String,
    val kind: String,
    val totalQuestionCount: Int,
    val answeredCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val skippedCount: Int,
    val revealedCount: Int,
    val accuracyPercent: Int,
    @ColumnInfo(name = "completed_at_ms")
    val completedAtMs: Long
)

internal fun PracticeSessionReportRecord.toEntity(): PracticeReportEntity {
    return PracticeReportEntity(
        sessionId = sessionId,
        kind = kind.name,
        totalQuestionCount = report.totalQuestionCount,
        answeredCount = report.answeredCount,
        correctCount = report.correctCount,
        wrongCount = report.wrongCount,
        skippedCount = report.skippedCount,
        revealedCount = report.revealedCount,
        accuracyPercent = report.accuracyPercent,
        completedAtMs = completedAtMs
    )
}

internal fun PracticeReportEntity.toDomain(): PracticeSessionReportRecord? {
    val kind = runCatching { PracticeKind.valueOf(kind) }.getOrNull() ?: return null
    return PracticeSessionReportRecord(
        sessionId = sessionId,
        kind = kind,
        report = PracticeReport(
            totalQuestionCount = totalQuestionCount,
            answeredCount = answeredCount,
            correctCount = correctCount,
            wrongCount = wrongCount,
            skippedCount = skippedCount,
            revealedCount = revealedCount,
            accuracyPercent = accuracyPercent
        ),
        completedAtMs = completedAtMs
    )
}
