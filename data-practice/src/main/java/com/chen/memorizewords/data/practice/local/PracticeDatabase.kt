package com.chen.memorizewords.data.practice.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chen.memorizewords.data.practice.local.room.model.practice.daily.DailyPracticeDurationDao
import com.chen.memorizewords.data.practice.local.room.model.practice.daily.DailyPracticeDurationEntity
import com.chen.memorizewords.data.practice.local.room.model.practice.exam.ExamPracticeDao
import com.chen.memorizewords.data.practice.local.room.model.practice.exam.ExamPracticeItemEntity
import com.chen.memorizewords.data.practice.local.room.model.practice.exam.ExamPracticeItemStateEntity
import com.chen.memorizewords.data.practice.local.room.model.practice.exam.ExamPracticeWordMetaEntity
import com.chen.memorizewords.data.practice.local.room.model.practice.session.PracticeSessionRecordDao
import com.chen.memorizewords.data.practice.local.room.model.practice.session.PracticeSessionRecordEntity
import com.chen.memorizewords.data.practice.local.room.model.practice.session.PracticeSessionWordEntity

@Database(
    entities = [
        PracticeReportEntity::class,
        DailyPracticeDurationEntity::class,
        ExamPracticeWordMetaEntity::class,
        ExamPracticeItemEntity::class,
        ExamPracticeItemStateEntity::class,
        PracticeSessionRecordEntity::class,
        PracticeSessionWordEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(PracticeRoomConverters::class)
abstract class PracticeDatabase : RoomDatabase() {
    abstract fun practiceReportDao(): PracticeReportDao
    abstract fun dailyPracticeDurationDao(): DailyPracticeDurationDao
    abstract fun examPracticeDao(): ExamPracticeDao
    abstract fun practiceSessionRecordDao(): PracticeSessionRecordDao
}
