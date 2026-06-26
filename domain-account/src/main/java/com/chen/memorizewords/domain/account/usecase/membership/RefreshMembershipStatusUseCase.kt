package com.chen.memorizewords.domain.account.usecase.membership

import com.chen.memorizewords.domain.account.repository.membership.MembershipRepository
import javax.inject.Inject

class RefreshMembershipStatusUseCase @Inject constructor(
    private val repository: MembershipRepository
) {
    suspend operator fun invoke() = repository.refreshStatus()
}
