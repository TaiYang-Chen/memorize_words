package com.chen.memorizewords.domain.model.study.record

enum class CheckInType {
    AUTO,
    MAKEUP
}

data class CheckInRecord(
    val date: String,
    val type: CheckInType,
    val signedAt: Long,
    val updatedAt: Long
)

data class TodayCheckInEntryState(
    val businessDate: String,
    val eligible: Boolean,
    val alreadyCheckedIn: Boolean
) {
    val shouldNavigate: Boolean
        get() = eligible && !alreadyCheckedIn
}

sealed class MakeUpCheckInException(message: String) : IllegalStateException(message) {
    data object FutureDate : MakeUpCheckInException("Can only make up previous dates")
    data object BalanceUnknown : MakeUpCheckInException("Makeup card balance unavailable")
    data object NoAvailableCard : MakeUpCheckInException("No makeup card available")
}

data class DayCheckInDetail(
    val date: String,
    val record: CheckInRecord? = null,
    val canMakeUp: Boolean = false,
    val availableMakeupCardCount: Int? = null
)
