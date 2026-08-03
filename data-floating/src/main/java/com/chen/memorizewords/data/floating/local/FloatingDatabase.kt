package com.chen.memorizewords.data.floating.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingRuntimeSessionDao
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingRuntimeSessionEntity
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingWordDisplayRecordDao
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingWordDisplayRecordEntity
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingWordDisplayWordEntity

@Database(
    entities = [
        FloatingWordDisplayRecordEntity::class,
        FloatingWordDisplayWordEntity::class,
        FloatingRuntimeSessionEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class FloatingDatabase : RoomDatabase() {
    abstract fun floatingWordDisplayRecordDao(): FloatingWordDisplayRecordDao
    abstract fun floatingRuntimeSessionDao(): FloatingRuntimeSessionDao
}
