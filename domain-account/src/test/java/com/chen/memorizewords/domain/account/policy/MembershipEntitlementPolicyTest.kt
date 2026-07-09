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
                validUntilAtMs = VALID_UNTIL_FUTURE,
                remainingDays = 1
            ),
            currentTimeMillis = NOW
        )

        assertEquals(MembershipFeatureAccess.ALLOWED, access)
    }

    @Test
    fun `floating review requires membership for inactive membership`() {
        val access = policy.resolve(
            feature = MembershipFeature.FLOATING_REVIEW,
            status = MembershipStatus(active = false),
            currentTimeMillis = NOW
        )

        assertEquals(MembershipFeatureAccess.MEMBERSHIP_REQUIRED, access)
    }

    @Test
    fun `floating review requires membership when status is missing`() {
        val access = policy.resolve(
            feature = MembershipFeature.FLOATING_REVIEW,
            status = null,
            currentTimeMillis = NOW
        )

        assertEquals(MembershipFeatureAccess.MEMBERSHIP_REQUIRED, access)
    }

    @Test
    fun `expired cached active membership is normalized before resolving access`() {
        val normalized = normalizeMembershipStatus(
            status = MembershipStatus(
                active = true,
                validUntilDate = "2026-06-23",
                validUntilAtMs = VALID_UNTIL_PAST,
                remainingDays = 1
            ),
            currentTimeMillis = NOW
        )

        val access = policy.resolve(
            feature = MembershipFeature.FLOATING_REVIEW,
            status = normalized,
            currentTimeMillis = NOW
        )

        assertEquals(MembershipFeatureAccess.MEMBERSHIP_REQUIRED, access)
    }

    @Test
    fun `cached active membership remains valid until future minute`() {
        val normalized = normalizeMembershipStatus(
            status = MembershipStatus(
                active = true,
                validUntilDate = "2026-06-24",
                validUntilAtMs = VALID_UNTIL_FUTURE,
                remainingDays = 7
            ),
            currentTimeMillis = NOW
        )

        val access = policy.resolve(
            feature = MembershipFeature.FLOATING_REVIEW,
            status = normalized,
            currentTimeMillis = NOW
        )

        assertEquals(MembershipFeatureAccess.ALLOWED, access)
        assertEquals(1, normalized?.remainingDays)
    }

    @Test
    fun `cached active membership without timestamp requires membership`() {
        val normalized = normalizeMembershipStatus(
            status = MembershipStatus(
                active = true,
                validUntilDate = "2026-06-24",
                remainingDays = 1
            ),
            currentTimeMillis = NOW
        )

        val access = policy.resolve(
            feature = MembershipFeature.FLOATING_REVIEW,
            status = normalized,
            currentTimeMillis = NOW
        )

        assertEquals(MembershipFeatureAccess.MEMBERSHIP_REQUIRED, access)
        assertEquals(false, normalized?.active)
        assertEquals(0, normalized?.remainingDays)
    }

    @Test
    fun `cached active membership expires at exact expiry minute`() {
        val normalized = normalizeMembershipStatus(
            status = MembershipStatus(
                active = true,
                validUntilAtMs = NOW,
                remainingDays = 1
            ),
            currentTimeMillis = NOW
        )

        assertEquals(false, normalized?.active)
        assertEquals(0, normalized?.remainingDays)
    }

    private companion object {
        const val NOW = 1_782_275_640_000L
        const val VALID_UNTIL_FUTURE = 1_782_279_240_000L
        const val VALID_UNTIL_PAST = 1_782_272_040_000L
    }
}
