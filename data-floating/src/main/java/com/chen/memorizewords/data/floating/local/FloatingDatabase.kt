package com.chen.memorizewords.data.floating.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingWordDisplayRecordDao
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingWordDisplayRecordEntity
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingWordDisplayWordEntity

@Database(
    entities = [
        FloatingWordDisplayRecordEntity::class,
        FloatingWordDisplayWordEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FloatingDatabase : RoomDatabase() {
    abstract fun floatingWordDisplayRecordDao(): FloatingWordDisplayRecordDao
}
