package com.chen.memorizewords.data.study.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordDao
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordEntity
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationDao
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationEntity
import com.chen.memorizewords.data.study.local.room.model.study.daily.WordStudyRecordsDao
import com.chen.memorizewords.data.study.local.room.model.study.daily.WordStudyRecordsEntity
import com.chen.memorizewords.data.study.local.room.model.study.favorites.WordFavoriteEntity
import com.chen.memorizewords.data.study.local.room.model.study.favorites.WordFavoritesDao
import com.chen.memorizewords.data.study.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.study.local.room.model.study.progress.word.WordLearningStateEntity
import com.chen.memorizewords.data.study.local.room.model.study.progress.wordbook.WordBookProgressDao
import com.chen.memorizewords.data.study.local.room.model.study.progress.wordbook.WordBookProgressEntity

@Database(
    entities = [
        CheckInRecordEntity::class,
        DailyStudyDurationEntity::class,
        WordStudyRecordsEntity::class,
        WordFavoriteEntity::class,
        WordLearningStateEntity::class,
        WordBookProgressEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(StudyRoomConverters::class)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun checkInRecordDao(): CheckInRecordDao
    abstract fun dailyStudyDurationDao(): DailyStudyDurationDao
    abstract fun wordStudyRecordsDao(): WordStudyRecordsDao
    abstract fun wordFavoritesDao(): WordFavoritesDao
    abstract fun wordLearningStateDao(): WordLearningStateDao
    abstract fun wordBookProgressDao(): WordBookProgressDao
}
