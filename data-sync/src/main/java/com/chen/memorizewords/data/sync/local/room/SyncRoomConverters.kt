package com.chen.memorizewords.data.sync.local.room

import androidx.room.TypeConverter
import com.chen.memorizewords.data.sync.repository.sync.SyncOutboxFailureKind
import com.chen.memorizewords.data.sync.repository.sync.SyncOutboxOperation
import com.chen.memorizewords.data.sync.repository.sync.SyncOutboxState

class SyncRoomConverters {
    @TypeConverter
    fun fromSyncOutboxOperation(value: SyncOutboxOperation?): String? = value?.name

    @TypeConverter
    fun toSyncOutboxOperation(value: String?): SyncOutboxOperation? =
        value?.let(SyncOutboxOperation::valueOf)

    @TypeConverter
    fun fromSyncOutboxState(value: SyncOutboxState?): String? = value?.name

    @TypeConverter
    fun toSyncOutboxState(value: String?): SyncOutboxState? =
        value?.let(SyncOutboxState::valueOf)

    @TypeConverter
    fun fromSyncOutboxFailureKind(value: SyncOutboxFailureKind?): String? = value?.name

    @TypeConverter
    fun toSyncOutboxFailureKind(value: String?): SyncOutboxFailureKind? =
        value?.let(SyncOutboxFailureKind::valueOf)
}
