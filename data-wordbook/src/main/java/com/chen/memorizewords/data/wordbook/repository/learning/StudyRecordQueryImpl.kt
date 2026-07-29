package com.chen.memorizewords.data.wordbook.repository.learning

import com.chen.memorizewords.data.wordbook.local.room.model.learning.record.WordStudyRecordDao
import com.chen.memorizewords.domain.study.model.learning.DailyWordCounts
import com.chen.memorizewords.domain.study.model.record.DailyStudyWordRecord
import com.chen.memorizewords.domain.study.model.record.DailyWordStats
import com.chen.memorizewords.domain.study.repository.record.StudyRecordQuery
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StudyRecordQueryImpl @Inject constructor(
    private val dao: WordStudyRecordDao
) : StudyRecordQuery {
    override fun getStudyTotalDayCount(): Flow<Int> = dao.getStudyTotalDayCount()

    override fun getNewWordCount(date: String): Flow<Int> = dao.getTodayNewWordCount(date)

    override fun getReviewWordCount(date: String): Flow<Int> = dao.getTodayReviewWordCount(date)

    override suspend fun getWordCounts(date: String): DailyWordCounts = DailyWordCounts(
        newCount = dao.getNewWordCount(date),
        reviewCount = dao.getReviewWordCount(date)
    )

    override fun getDailyWordStats(
        startDate: String,
        endDate: String
    ): Flow<List<DailyWordStats>> = dao.getDailyWordStats(startDate, endDate).map { rows ->
        rows.map { DailyWordStats(date = it.date, newCount = it.newCount, reviewCount = it.reviewCount) }
    }

    override fun getDailyStudyWordRecords(date: String): Flow<List<DailyStudyWordRecord>> =
        dao.getDailyStudyWordRecords(date).map { rows ->
            rows.map {
                DailyStudyWordRecord(
                    wordId = it.wordId,
                    word = it.word,
                    definition = it.definition,
                    isNewWord = it.isNewWord
                )
            }
        }

    override fun observeStudyDatesBetween(startDate: String, endDate: String): Flow<List<String>> =
        dao.observeStudyDatesBetween(startDate, endDate)
}
