package com.chen.memorizewords.data.study.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordDao
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordEntity
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationDao
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationEntity
import com.chen.memorizewords.data.study.local.room.model.study.favorites.WordFavoriteEntity
import com.chen.memorizewords.data.study.local.room.model.study.favorites.WordFavoritesDao
import com.chen.memorizewords.data.study.local.room.model.study.outbox.StudyPendingOutboxDao
import com.chen.memorizewords.data.study.local.room.model.study.outbox.StudyPendingOutboxEntity

@Database(
    entities = [
        CheckInRecordEntity::class,
        DailyStudyDurationEntity::class,
        WordFavoriteEntity::class,
        StudyPendingOutboxEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(StudyRoomConverters::class)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun checkInRecordDao(): CheckInRecordDao
    abstract fun dailyStudyDurationDao(): DailyStudyDurationDao
    abstract fun wordFavoritesDao(): WordFavoritesDao
    abstract fun studyPendingOutboxDao(): StudyPendingOutboxDao
}
