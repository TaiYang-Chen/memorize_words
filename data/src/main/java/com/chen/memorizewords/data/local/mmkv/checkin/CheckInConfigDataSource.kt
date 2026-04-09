package com.chen.memorizewords.data.local.mmkv.checkin

import kotlinx.coroutines.flow.Flow

const val DEFAULT_DAY_BOUNDARY_OFFSET_MINUTES = 240

data class CheckInConfig(
    val dayBoundaryOffsetMinutes: Int = DEFAULT_DAY_BOUNDARY_OFFSET_MINUTES,
    val timezoneId: String = "",
    val cachedMakeupCardBalance: Int = UNKNOWN_MAKEUP_CARD_BALANCE,
    val lastCheckInSyncAt: Long = 0L
)

interface CheckInConfigDataSource {
    fun getConfig(): CheckInConfig
    fun getConfigFlow(): Flow<CheckInConfig>
    fun saveDayBoundaryOffsetMinutes(offsetMinutes: Int)
    fun saveTimezoneId(timezoneId: String)
    fun saveCachedMakeupCardBalance(balance: Int)
    fun consumeCachedMakeupCardBalance(count: Int = 1)
    fun saveLastCheckInSyncAt(timestamp: Long)
    fun clearUserScopedState()
}

const val UNKNOWN_MAKEUP_CARD_BALANCE = -1
