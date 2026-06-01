package com.chen.memorizewords.data.practice

import com.chen.memorizewords.data.practice.local.PracticeReportDao
import com.chen.memorizewords.data.practice.local.toDomain
import com.chen.memorizewords.data.practice.local.toEntity
import com.chen.memorizewords.domain.practice.PracticeKind
import com.chen.memorizewords.domain.practice.PracticeReportRepository
import com.chen.memorizewords.domain.practice.PracticeSessionReportRecord
import javax.inject.Inject

class PracticeReportRepositoryImpl @Inject constructor(
    private val practiceReportDao: PracticeReportDao
) : PracticeReportRepository {
    override suspend fun save(record: PracticeSessionReportRecord) {
        practiceReportDao.upsert(record.toEntity())
    }

    override suspend fun getLatest(kind: PracticeKind): PracticeSessionReportRecord? {
        return practiceReportDao.latestByKind(kind.name)?.toDomain()
    }

    override suspend fun getBySessionId(sessionId: String): PracticeSessionReportRecord? {
        return practiceReportDao.bySessionId(sessionId)?.toDomain()
    }
}
