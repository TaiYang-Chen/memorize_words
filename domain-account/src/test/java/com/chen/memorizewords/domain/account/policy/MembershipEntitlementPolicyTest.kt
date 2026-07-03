package com.chen.memorizewords.domain.account.policy

import com.chen.memorizewords.domain.account.model.membership.MembershipFeature
import com.chen.memorizewords.domain.account.model.membership.MembershipFeatureAccess
import com.chen.memorizewords.domain.account.model.membership.MembershipStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class MembershipEntitlementPolicyTest {
    private val policy = MembershipEntitlementPolicy()

    @Test
    fun `floating review is allowed for active membership`() {
        val access = policy.resolve(
            feature = MembershipFeature.FLOATING_REVIEW,
            status = MembershipStatus(active = true)
        )

        assertEquals(MembershipFeatureAccess.ALLOWED, access)
    }

    @Test
    fun `floating review requires membership for inactive membership`() {
        val access = policy.resolve(
            feature = MembershipFeature.FLOATING_REVIEW,
            status = MembershipStatus(active = false)
        )

        assertEquals(MembershipFeatureAccess.MEMBERSHIP_REQUIRED, access)
    }

    @Test
    fun `floating review requires membership when status is missing`() {
        val access = policy.resolve(
            feature = MembershipFeature.FLOATING_REVIEW,
            status = null
        )

        assertEquals(MembershipFeatureAccess.MEMBERSHIP_REQUIRED, access)
    }
}
