package com.chen.memorizewords.data.account.floating

import com.chen.memorizewords.domain.account.auth.AuthStateProvider
import com.chen.memorizewords.domain.account.model.membership.MembershipFeature
import com.chen.memorizewords.domain.account.model.membership.MembershipFeatureAccess
import com.chen.memorizewords.domain.account.usecase.membership.ResolveMembershipFeatureAccessUseCase
import com.chen.memorizewords.domain.floating.model.FloatingActivationEligibility
import com.chen.memorizewords.domain.floating.service.FloatingActivationEligibilityChecker
import javax.inject.Inject

class AccountFloatingActivationEligibilityChecker @Inject constructor(
    private val authStateProvider: AuthStateProvider,
    private val resolveMembershipFeatureAccess: ResolveMembershipFeatureAccessUseCase
) : FloatingActivationEligibilityChecker {
    override suspend fun checkEligibility(): FloatingActivationEligibility {
        if (!authStateProvider.isAuthenticated()) {
            return FloatingActivationEligibility.AUTHENTICATION_REQUIRED
        }
        return if (
            resolveMembershipFeatureAccess(MembershipFeature.FLOATING_REVIEW) ==
            MembershipFeatureAccess.ALLOWED
        ) {
            FloatingActivationEligibility.ELIGIBLE
        } else {
            FloatingActivationEligibility.MEMBERSHIP_REQUIRED
        }
    }
}
