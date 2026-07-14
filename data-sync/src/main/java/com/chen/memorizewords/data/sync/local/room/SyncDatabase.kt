package com.chen.memorizewords.data.sync.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxEntity
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncEventDao
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncEventEntity

@Database(
    entities = [
        SyncOutboxEntity::class,
        FailedSyncEventEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(SyncRoomConverters::class)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun failedSyncEventDao(): FailedSyncEventDao
}
