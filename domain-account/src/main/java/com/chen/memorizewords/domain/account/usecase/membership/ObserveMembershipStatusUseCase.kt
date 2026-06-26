package com.chen.memorizewords.domain.account.usecase.membership

import com.chen.memorizewords.domain.account.repository.membership.MembershipRepository
import javax.inject.Inject

class ObserveMembershipStatusUseCase @Inject constructor(
    private val repository: MembershipRepository
) {
    operator fun invoke() = repository.observeStatus()
}
