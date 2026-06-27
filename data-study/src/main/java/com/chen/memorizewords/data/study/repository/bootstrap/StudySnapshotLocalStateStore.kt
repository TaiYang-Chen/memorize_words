package com.chen.memorizewords.data.study.repository.bootstrap

import androidx.room.withTransaction
import com.chen.memorizewords.data.study.local.StudyDatabase
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordDao
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordEntity
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationDao
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationEntity
import com.chen.memorizewords.data.study.local.room.model.study.daily.WordStudyRecordsDao
import com.chen.memorizewords.data.study.local.room.model.study.daily.toEntity
import com.chen.memorizewords.data.study.local.room.model.study.favorites.WordFavoritesDao
import com.chen.memorizewords.data.study.local.room.model.study.favorites.parseFavoriteAddedAt
import com.chen.memorizewords.data.study.local.room.model.study.favorites.toEntity
import com.chen.memorizewords.data.study.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.study.local.room.model.study.progress.word.toEntity
import com.chen.memorizewords.domain.study.model.favorites.WordFavorites
import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.model.record.DailyStudyRecords
import com.chen.memorizewords.domain.study.repository.StudyDailyDurationSnapshot
import com.chen.memorizewords.domain.study.repository.StudySnapshotLocalStatePort
import javax.inject.Inject

class StudySnapshotLocalStateStore @Inject constructor(
    private val studyDatabase: StudyDatabase,
    private val favoritesDao: WordFavoritesDao,
    private val wordLearningStateDao: WordLearningStateDao,
    private val wordStudyRecordsDao: WordStudyRecordsDao,
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

    override suspend fun overwriteLearningStatesForBookFromRemote(
        bookId: Long,
        states: List<WordLearningState>
    ) {
        studyDatabase.withTransaction {
            wordLearningStateDao.deleteLearningWordByBookId(bookId)
            if (states.isNotEmpty()) {
                wordLearningStateDao.upsertAll(states.map { it.toEntity() })
            }
        }
    }

    override suspend fun overwriteStudyRecordsFromRemote(records: List<DailyStudyRecords>) {
        studyDatabase.withTransaction {
            wordStudyRecordsDao.deleteAll()
            if (records.isNotEmpty()) {
                wordStudyRecordsDao.upsertAll(records.map { it.toEntity() })
            }
        }
    }

    override suspend fun upsertStudyRecordsFromRemote(records: List<DailyStudyRecords>) {
        if (records.isEmpty()) return
        studyDatabase.withTransaction {
            wordStudyRecordsDao.upsertAll(records.map { it.toEntity() })
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
        signedAt = signedAt,
        updatedAt = updatedAt
    )
}

private fun StudyDailyDurationSnapshot.toEntity(): DailyStudyDurationEntity {
    return DailyStudyDurationEntity(
        date = date,
        totalDurationMs = totalDurationMs,
        updatedAt = updatedAt,
        isNewPlanCompleted = isNewPlanCompleted,
        isReviewPlanCompleted = isReviewPlanCompleted
    )
}
