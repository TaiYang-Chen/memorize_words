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
            status = MembershipStatus(
                active = true,
                validUntilDate = "2026-06-24",
                remainingDays = 1
            ),
            currentDate = "2026-06-24"
        )

        assertEquals(MembershipFeatureAccess.ALLOWED, access)
    }

    @Test
    fun `floating review requires membership for inactive membership`() {
        val access = policy.resolve(
            feature = MembershipFeature.FLOATING_REVIEW,
            status = MembershipStatus(active = false),
            currentDate = "2026-06-24"
        )

        assertEquals(MembershipFeatureAccess.MEMBERSHIP_REQUIRED, access)
    }

    @Test
    fun `floating review requires membership when status is missing`() {
        val access = policy.resolve(
            feature = MembershipFeature.FLOATING_REVIEW,
            status = null,
            currentDate = "2026-06-24"
        )

        assertEquals(MembershipFeatureAccess.MEMBERSHIP_REQUIRED, access)
    }

    @Test
    fun `expired cached active membership is normalized before resolving access`() {
        val normalized = normalizeMembershipStatus(
            status = MembershipStatus(
                active = true,
                validUntilDate = "2026-06-23",
                remainingDays = 1
            ),
            currentDate = "2026-06-24"
        )

        val access = policy.resolve(
            feature = MembershipFeature.FLOATING_REVIEW,
            status = normalized,
            currentDate = "2026-06-24"
        )

        assertEquals(MembershipFeatureAccess.MEMBERSHIP_REQUIRED, access)
    }

    @Test
    fun `cached active membership remains valid through valid until date`() {
        val normalized = normalizeMembershipStatus(
            status = MembershipStatus(
                active = true,
                validUntilDate = "2026-06-24",
                remainingDays = 7
            ),
            currentDate = "2026-06-24"
        )

        val access = policy.resolve(
            feature = MembershipFeature.FLOATING_REVIEW,
            status = normalized,
            currentDate = "2026-06-24"
        )

        assertEquals(MembershipFeatureAccess.ALLOWED, access)
        assertEquals(1, normalized?.remainingDays)
    }

    @Test
    fun `cached active membership with invalid date requires membership`() {
        val normalized = normalizeMembershipStatus(
            status = MembershipStatus(
                active = true,
                validUntilDate = "2026-02-31",
                remainingDays = 1
            ),
            currentDate = "2026-02-28"
        )

        val access = policy.resolve(
            feature = MembershipFeature.FLOATING_REVIEW,
            status = normalized,
            currentDate = "2026-02-28"
        )

        assertEquals(MembershipFeatureAccess.MEMBERSHIP_REQUIRED, access)
        assertEquals(false, normalized?.active)
        assertEquals(0, normalized?.remainingDays)
    }
}
