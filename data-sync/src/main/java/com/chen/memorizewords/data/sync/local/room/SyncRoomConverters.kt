package com.chen.memorizewords.data.sync.local.room

import androidx.room.TypeConverter
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncDeliveryMode
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncState

class SyncRoomConverters {
    @TypeConverter
    fun fromFailedSyncDeliveryMode(value: FailedSyncDeliveryMode?): String? = value?.name

    @TypeConverter
    fun toFailedSyncDeliveryMode(value: String?): FailedSyncDeliveryMode? =
        value?.let(FailedSyncDeliveryMode::valueOf)

    @TypeConverter
    fun fromFailedSyncState(value: FailedSyncState?): String? = value?.name

    @TypeConverter
    fun toFailedSyncState(value: String?): FailedSyncState? = value?.let(FailedSyncState::valueOf)

}
