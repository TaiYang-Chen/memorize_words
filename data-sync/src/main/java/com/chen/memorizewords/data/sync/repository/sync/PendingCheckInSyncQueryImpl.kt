package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncEventDao
import com.chen.memorizewords.domain.sync.FailureQueueEventType
import com.chen.memorizewords.domain.sync.PendingCheckInSyncQuery
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PendingCheckInSyncQueryImpl @Inject constructor(
    private val dao: FailedSyncEventDao,
    private val moshi: Moshi
) : PendingCheckInSyncQuery {
    override fun observePendingMakeupCheckInCount(): Flow<Int> =
        dao.observePendingByEventType(FailureQueueEventType.CHECKIN_RECORD)
            .map(::countMakeupEvents)

    override suspend fun countPendingMakeupCheckIns(): Int {
        return countMakeupEvents(
            dao.getPendingByEventType(FailureQueueEventType.CHECKIN_RECORD)
        )
    }

    private fun countMakeupEvents(events: List<com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncEventEntity>): Int {
        val adapter = moshi.adapter(Map::class.java)
        return events.count { event ->
            val params = runCatching { adapter.fromJson(event.paramsJson) }.getOrNull()
            params?.get("type")?.toString() == "MAKEUP"
        }
    }
}
