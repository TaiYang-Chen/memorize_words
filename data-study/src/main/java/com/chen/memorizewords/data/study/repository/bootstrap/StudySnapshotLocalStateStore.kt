package com.chen.memorizewords.data.study.repository.bootstrap

import androidx.room.withTransaction
import com.chen.memorizewords.data.study.local.StudyDatabase
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordDao
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordEntity
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationDao
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationEntity
import com.chen.memorizewords.data.study.local.room.model.study.favorites.WordFavoritesDao
import com.chen.memorizewords.data.study.local.room.model.study.favorites.parseFavoriteAddedAt
import com.chen.memorizewords.data.study.local.room.model.study.favorites.toEntity
import com.chen.memorizewords.data.wordbook.local.WordBookDatabase
import com.chen.memorizewords.data.wordbook.local.room.model.learning.record.WordStudyRecordDao as LearningWordStudyRecordDao
import com.chen.memorizewords.data.wordbook.local.room.model.learning.record.WordStudyRecordEntity
import com.chen.memorizewords.domain.study.model.favorites.WordFavorites
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.model.record.DailyStudyRecords
import com.chen.memorizewords.domain.study.repository.StudyDailyDurationSnapshot
import com.chen.memorizewords.domain.study.repository.StudySnapshotLocalStatePort
import javax.inject.Inject

class StudySnapshotLocalStateStore @Inject constructor(
    private val studyDatabase: StudyDatabase,
    private val wordBookDatabase: WordBookDatabase,
    private val favoritesDao: WordFavoritesDao,
    private val learningWordStudyRecordDao: LearningWordStudyRecordDao,
    private val dailyStudyDurationDao: DailyStudyDurationDao,
    private val checkInRecordDao: CheckInRecordDao
) : StudySnapshotLocalStatePort {

    override suspend fun overwriteFavoritesFromRemote(favorites: List<WordFavorites>) {
        studyDatabase.withTransaction {
            favoritesDao.deleteAll()
            if (favorites.isNotEmpty()) {
                favoritesDao.upsertAll(
                    favorites.map { favorite ->
                        favorite.toEntity(
                            addedAt = parseFavoriteAddedAt(favorite.addedDate)
                        )
                    }
                )
            }
        }
    }

    override suspend fun overwriteStudyRecordsFromRemote(records: List<DailyStudyRecords>) {
        wordBookDatabase.withTransaction {
            learningWordStudyRecordDao.deleteAll()
            if (records.isNotEmpty()) {
                learningWordStudyRecordDao.upsertAll(records.map { it.toLearningEntity() })
            }
        }
    }

    override suspend fun upsertStudyRecordsFromRemote(records: List<DailyStudyRecords>) {
        if (records.isEmpty()) return
        wordBookDatabase.withTransaction {
            learningWordStudyRecordDao.upsertAll(records.map { it.toLearningEntity() })
        }
    }

    override suspend fun overwriteDailyDurationsFromRemote(durations: List<StudyDailyDurationSnapshot>) {
        studyDatabase.withTransaction {
            dailyStudyDurationDao.deleteAll()
            if (durations.isNotEmpty()) {
                dailyStudyDurationDao.upsertAll(durations.map { it.toEntity() })
            }
        }
    }

    override suspend fun upsertDailyDurationsFromRemote(durations: List<StudyDailyDurationSnapshot>) {
        if (durations.isEmpty()) return
        studyDatabase.withTransaction {
            dailyStudyDurationDao.upsertAll(durations.map { it.toEntity() })
        }
    }

    override suspend fun overwriteCheckInRecordsFromRemote(records: List<CheckInRecord>) {
        studyDatabase.withTransaction {
            checkInRecordDao.deleteAll()
            if (records.isNotEmpty()) {
                checkInRecordDao.upsertAll(records.map(CheckInRecord::toEntity))
            }
        }
    }
}

private fun CheckInRecord.toEntity(): CheckInRecordEntity {
    return CheckInRecordEntity(
        date = date,
        type = type,
        signedAtMs = signedAtMs,
        updatedAtMs = updatedAtMs
    )
}

private fun StudyDailyDurationSnapshot.toEntity(): DailyStudyDurationEntity {
    return DailyStudyDurationEntity(
        date = date,
        totalDurationMs = totalDurationMs,
        updatedAtMs = updatedAtMs,
        isNewPlanCompleted = isNewPlanCompleted,
        isReviewPlanCompleted = isReviewPlanCompleted
    )
}

private fun DailyStudyRecords.toLearningEntity(): WordStudyRecordEntity {
    return WordStudyRecordEntity(
        date = date,
        wordId = wordId,
        word = word,
        definition = definition,
        isNewWord = isNewWord
    )
}
