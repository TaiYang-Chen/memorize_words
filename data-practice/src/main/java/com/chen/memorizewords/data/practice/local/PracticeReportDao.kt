package com.chen.memorizewords.data.practice.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PracticeReportDao {
    @Upsert
    suspend fun upsert(entity: PracticeReportEntity)

    @Query(
        """
        SELECT * FROM practice_report
        WHERE kind = :kind
        ORDER BY completed_at_ms DESC
        LIMIT 1
        """
    )
    suspend fun latestByKind(kind: String): PracticeReportEntity?

    @Query("SELECT * FROM practice_report WHERE sessionId = :sessionId LIMIT 1")
    suspend fun bySessionId(sessionId: String): PracticeReportEntity?
}
