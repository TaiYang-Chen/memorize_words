package com.chen.memorizewords.domain.account.repository.membership

import com.chen.memorizewords.domain.account.model.membership.MembershipCheckInReward
import com.chen.memorizewords.domain.account.model.membership.MembershipStatus
import kotlinx.coroutines.flow.Flow

interface MembershipRepository {
    fun observeStatus(): Flow<MembershipStatus?>
    suspend fun refreshStatus(): Result<MembershipStatus>
    suspend fun checkIn(): Result<MembershipCheckInReward>
}
