package com.chen.memorizewords.data.sync.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxEntity

@Database(
    entities = [
        SyncOutboxEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(SyncRoomConverters::class)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun syncOutboxDao(): SyncOutboxDao
}
