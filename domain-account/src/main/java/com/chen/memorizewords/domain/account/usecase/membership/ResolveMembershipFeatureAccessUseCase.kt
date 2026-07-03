package com.chen.memorizewords.domain.account.usecase.membership

import com.chen.memorizewords.domain.account.model.membership.MembershipFeature
import com.chen.memorizewords.domain.account.policy.MembershipEntitlementPolicy
import com.chen.memorizewords.domain.account.repository.membership.MembershipRepository
import javax.inject.Inject

class ResolveMembershipFeatureAccessUseCase @Inject constructor(
    private val repository: MembershipRepository,
    private val policy: MembershipEntitlementPolicy
) {
    suspend operator fun invoke(feature: MembershipFeature) =
        policy.resolve(feature, repository.getCachedStatus())
}
