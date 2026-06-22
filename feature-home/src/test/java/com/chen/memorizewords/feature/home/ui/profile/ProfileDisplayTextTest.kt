package com.chen.memorizewords.feature.home.ui.profile

import com.chen.memorizewords.domain.account.model.user.User
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileDisplayTextTest {

    @Test
    fun `profile identity display uses fallback for empty user`() {
        assertEquals("fallback", resolveProfileNickname(null, "fallback"))
        assertEquals("--", resolveProfileAccountId(null, "--"))
    }

    @Test
    fun `profile identity display uses real user values`() {
        val user = User(
            userId = 42L,
            email = null,
            nickname = " Alice ",
            gender = null,
            avatarUrl = null,
            phone = null,
            qq = null,
            wechat = null,
            emailVerified = false,
            onboardingCompleted = true
        )

        assertEquals("Alice", resolveProfileNickname(user, "fallback"))
        assertEquals("42", resolveProfileAccountId(user, "--"))
    }

    @Test
    fun `total duration display floors to hours`() {
        assertEquals("0", formatTotalDurationHours(-1L))
        assertEquals("0", formatTotalDurationHours(59 * 60_000L))
        assertEquals("1", formatTotalDurationHours(90 * 60_000L))
        assertEquals("2", formatTotalDurationHours(2 * 3_600_000L))
    }

    @Test
    fun `signed word count display supports positive zero and negative values`() {
        assertEquals("+3", formatSignedInt(3))
        assertEquals("0", formatSignedInt(0))
        assertEquals("-2", formatSignedInt(-2))
    }

    @Test
    fun `signed hour display supports positive zero and negative values`() {
        assertEquals("+1.5h", formatSignedHours(90 * 60_000L))
        assertEquals("0h", formatSignedHours(0L))
        assertEquals("-2h", formatSignedHours(-2 * 3_600_000L))
    }

    @Test
    fun `previous business date handles month boundary`() {
        assertEquals("2026-06-21", previousBusinessDate("2026-06-22"))
        assertEquals("2026-05-31", previousBusinessDate("2026-06-01"))
    }
}
