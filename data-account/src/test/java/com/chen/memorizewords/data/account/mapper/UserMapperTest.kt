package com.chen.memorizewords.data.account.mapper

import com.chen.memorizewords.data.account.remoteapi.dto.ProfileDto
import com.chen.memorizewords.data.account.remoteapi.dto.UserDto
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingPhase
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserMapperTest {

    @Test
    fun `user dto onboardingCompleted field wins over onboarding snapshot`() {
        val user = createUserDto(onboardingCompleted = false)
            .toDomain(OnboardingSnapshot(phase = OnboardingPhase.COMPLETED, completedAt = 1L))

        assertFalse(user.onboardingCompleted)
    }

    @Test
    fun `user dto falls back to onboarding snapshot when field is missing`() {
        val user = createUserDto(onboardingCompleted = null)
            .toDomain(OnboardingSnapshot(phase = OnboardingPhase.COMPLETED, completedAt = 1L))

        assertTrue(user.onboardingCompleted)
    }

    @Test
    fun `profile dto maps onboardingCompleted field`() {
        val profile = ProfileDto(
            userId = 7L,
            email = "demo@example.com",
            nickname = "Demo",
            gender = null,
            avatarUrl = null,
            phone = null,
            qq = null,
            wechat = null,
            emailVerified = true,
            onboardingCompleted = true
        )

        assertTrue(profile.toDomain(onboardingCompleted = false).onboardingCompleted)
    }

    private fun createUserDto(onboardingCompleted: Boolean?): UserDto {
        return UserDto(
            id = 7L,
            email = "demo@example.com",
            nickname = "Demo",
            gender = null,
            avatarUrl = null,
            phone = null,
            qq = null,
            wechat = null,
            emailVerified = true,
            onboardingCompleted = onboardingCompleted
        )
    }
}
