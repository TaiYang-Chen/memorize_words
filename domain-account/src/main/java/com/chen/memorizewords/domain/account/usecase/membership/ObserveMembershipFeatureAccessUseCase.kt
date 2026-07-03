package com.chen.memorizewords.domain.account.usecase.membership

import com.chen.memorizewords.domain.account.model.membership.MembershipFeature
import com.chen.memorizewords.domain.account.policy.MembershipEntitlementPolicy
import com.chen.memorizewords.domain.account.repository.membership.MembershipRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ObserveMembershipFeatureAccessUseCase @Inject constructor(
    private val repository: MembershipRepository,
    private val policy: MembershipEntitlementPolicy
) {
    operator fun invoke(feature: MembershipFeature) =
        repository.observeStatus()
            .map { status -> policy.resolve(feature, status) }
            .distinctUntilChanged()
}
