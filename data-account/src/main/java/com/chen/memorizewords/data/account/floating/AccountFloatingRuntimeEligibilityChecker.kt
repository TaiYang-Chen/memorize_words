package com.chen.memorizewords.data.account.floating

import com.chen.memorizewords.domain.account.auth.AuthStateProvider
import com.chen.memorizewords.domain.account.model.membership.MembershipFeature
import com.chen.memorizewords.domain.account.model.membership.MembershipFeatureAccess
import com.chen.memorizewords.domain.account.usecase.membership.ResolveMembershipFeatureAccessUseCase
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeEligibility
import com.chen.memorizewords.domain.floating.service.FloatingRuntimeEligibilityChecker
import javax.inject.Inject

class AccountFloatingRuntimeEligibilityChecker @Inject constructor(
    private val authStateProvider: AuthStateProvider,
    private val resolveMembershipFeatureAccess: ResolveMembershipFeatureAccessUseCase
) : FloatingRuntimeEligibilityChecker {
    override suspend fun checkEligibility(): FloatingRuntimeEligibility {
        if (!authStateProvider.isAuthenticated()) {
            return FloatingRuntimeEligibility.AUTHENTICATION_REQUIRED
        }
        return if (
            resolveMembershipFeatureAccess(MembershipFeature.FLOATING_REVIEW) ==
            MembershipFeatureAccess.ALLOWED
        ) {
            FloatingRuntimeEligibility.ELIGIBLE
        } else {
            FloatingRuntimeEligibility.MEMBERSHIP_REQUIRED
        }
    }
}
