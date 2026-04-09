package com.chen.memorizewords.data.local.mmkv.checkin

import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.TimeZone
import javax.inject.Inject

class CheckInConfigDataSourceImpl @Inject constructor(
    private val mmkv: MMKV
) : CheckInConfigDataSource {

    private val configFlow = MutableStateFlow(loadConfig())

    override fun getConfig(): CheckInConfig = configFlow.value

    override fun getConfigFlow(): Flow<CheckInConfig> = configFlow

    override fun saveDayBoundaryOffsetMinutes(offsetMinutes: Int) {
        val normalized = offsetMinutes.coerceIn(0, 1439)
        val current = configFlow.value
        if (current.dayBoundaryOffsetMinutes == normalized) return
        mmkv.encode(KEY_DAY_BOUNDARY_OFFSET_MINUTES, normalized)
        configFlow.value = current.copy(dayBoundaryOffsetMinutes = normalized)
    }

    override fun saveTimezoneId(timezoneId: String) {
        if (timezoneId.isBlank()) return
        val current = configFlow.value
        if (current.timezoneId == timezoneId) return
        mmkv.encode(KEY_TIMEZONE_ID, timezoneId)
        configFlow.value = current.copy(timezoneId = timezoneId)
    }

    override fun saveCachedMakeupCardBalance(balance: Int) {
        val normalized = balance.coerceAtLeast(0)
        val current = configFlow.value
        if (current.cachedMakeupCardBalance == normalized) return
        mmkv.encode(KEY_CACHED_MAKEUP_CARD_BALANCE, normalized)
        configFlow.value = current.copy(cachedMakeupCardBalance = normalized)
    }

    override fun consumeCachedMakeupCardBalance(count: Int) {
        val safeCount = count.coerceAtLeast(0)
        if (safeCount == 0) return
        val current = configFlow.value
        if (current.cachedMakeupCardBalance == UNKNOWN_MAKEUP_CARD_BALANCE) return
        val normalized = (current.cachedMakeupCardBalance - safeCount).coerceAtLeast(0)
        if (current.cachedMakeupCardBalance == normalized) return
        mmkv.encode(KEY_CACHED_MAKEUP_CARD_BALANCE, normalized)
        configFlow.value = current.copy(cachedMakeupCardBalance = normalized)
    }

    override fun saveLastCheckInSyncAt(timestamp: Long) {
        val normalized = timestamp.coerceAtLeast(0L)
        val current = configFlow.value
        if (current.lastCheckInSyncAt == normalized) return
        mmkv.encode(KEY_LAST_CHECKIN_SYNC_AT, normalized)
        configFlow.value = current.copy(lastCheckInSyncAt = normalized)
    }

    override fun clearUserScopedState() {
        mmkv.removeValueForKey(KEY_DAY_BOUNDARY_OFFSET_MINUTES)
        mmkv.removeValueForKey(KEY_TIMEZONE_ID)
        mmkv.removeValueForKey(KEY_CACHED_MAKEUP_CARD_BALANCE)
        mmkv.removeValueForKey(KEY_LAST_CHECKIN_SYNC_AT)
        configFlow.value = loadConfig()
    }

    private fun loadConfig(): CheckInConfig {
        val timezoneId = mmkv.decodeString(KEY_TIMEZONE_ID, TimeZone.getDefault().id)
            ?: TimeZone.getDefault().id
        return CheckInConfig(
            dayBoundaryOffsetMinutes = mmkv.decodeInt(
                KEY_DAY_BOUNDARY_OFFSET_MINUTES,
                DEFAULT_DAY_BOUNDARY_OFFSET_MINUTES
            ),
            timezoneId = timezoneId,
            cachedMakeupCardBalance = mmkv.decodeInt(
                KEY_CACHED_MAKEUP_CARD_BALANCE,
                UNKNOWN_MAKEUP_CARD_BALANCE
            ),
            lastCheckInSyncAt = mmkv.decodeLong(KEY_LAST_CHECKIN_SYNC_AT, 0L)
        )
    }
}

private const val KEY_DAY_BOUNDARY_OFFSET_MINUTES = "checkin_day_boundary_offset_minutes"
private const val KEY_TIMEZONE_ID = "checkin_timezone_id"
private const val KEY_CACHED_MAKEUP_CARD_BALANCE = "checkin_cached_makeup_card_balance"
private const val KEY_LAST_CHECKIN_SYNC_AT = "checkin_last_sync_at"
