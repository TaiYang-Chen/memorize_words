package com.chen.memorizewords.domain.account.policy

import com.chen.memorizewords.domain.account.model.membership.MembershipFeature
import com.chen.memorizewords.domain.account.model.membership.MembershipFeatureAccess
import com.chen.memorizewords.domain.account.model.membership.MembershipStatus
import javax.inject.Inject

class MembershipEntitlementPolicy @Inject constructor() {
    fun resolve(
        feature: MembershipFeature,
        status: MembershipStatus?,
        currentDate: String = currentLocalMembershipDate()
    ): MembershipFeatureAccess {
        val normalizedStatus = normalizeMembershipStatus(status, currentDate)
        return when (feature) {
            MembershipFeature.FLOATING_REVIEW -> if (normalizedStatus?.active == true) {
                MembershipFeatureAccess.ALLOWED
            } else {
                MembershipFeatureAccess.MEMBERSHIP_REQUIRED
            }
        }
    }
}
