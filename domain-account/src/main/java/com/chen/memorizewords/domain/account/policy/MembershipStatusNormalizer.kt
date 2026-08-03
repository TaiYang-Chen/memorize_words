package com.chen.memorizewords.domain.account.policy

import com.chen.memorizewords.domain.account.model.membership.MembershipStatus
import kotlin.math.ceil

private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

fun normalizeMembershipStatus(
    status: MembershipStatus?,
    currentTimeMillis: Long = currentMembershipTimeMillis()
): MembershipStatus? {
    status ?: return null
    val validUntilAtMs = status.validUntilAtMs
    val active = validUntilAtMs != null && validUntilAtMs > currentTimeMillis
    return status.copy(
        active = active,
        validUntilAtMs = validUntilAtMs,
        remainingDays = if (active) {
            calculateRemainingDays(currentTimeMillis, validUntilAtMs)
        } else {
            0
        }
    )
}

fun currentMembershipTimeMillis(): Long = System.currentTimeMillis()

private fun calculateRemainingDays(currentTimeMillis: Long, validUntilAtMs: Long): Int {
    val remainingMillis = validUntilAtMs - currentTimeMillis
    if (remainingMillis <= 0L) return 0
    return ceil(remainingMillis.toDouble() / MILLIS_PER_DAY).toInt().coerceAtLeast(1)
}
