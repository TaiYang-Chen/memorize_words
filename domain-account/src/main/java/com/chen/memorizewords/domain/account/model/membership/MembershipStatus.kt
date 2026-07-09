package com.chen.memorizewords.domain.account.model.membership

data class MembershipStatus(
    val level: String = "PRO",
    val active: Boolean = false,
    val validUntilDate: String? = null,
    val validUntilAtMs: Long? = null,
    val remainingDays: Int = 0,
    val totalGrantedDays: Int = 0,
    val todayCheckedIn: Boolean = false
)

data class MembershipCheckInReward(
    val granted: Boolean,
    val grantDays: Int,
    val rewardDate: String,
    val membership: MembershipStatus
)
