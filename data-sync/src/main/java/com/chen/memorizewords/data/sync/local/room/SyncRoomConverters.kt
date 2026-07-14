package com.chen.memorizewords.data.sync.local.room

import androidx.room.TypeConverter
import com.chen.memorizewords.data.sync.repository.sync.SyncOutboxFailureKind
import com.chen.memorizewords.data.sync.repository.sync.SyncOutboxOperation
import com.chen.memorizewords.data.sync.repository.sync.SyncOutboxState
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
