package com.chen.memorizewords.domain.account.usecase.user
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

class BindSocialUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val localAccountRepository: LocalAccountRepository
) {
    suspend operator fun invoke(
        platform: String,
        oauthCode: String,
        state: String?
    ): Result<User> {
        val normalizedPlatform = platform.lowercase()
        if (normalizedPlatform != "wechat" && normalizedPlatform != "qq") {
            return Result.failure(IllegalArgumentException("Unsupported social platform: $platform"))
        }
        if (oauthCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Authorization code is required"))
        }
        return runCatching {
            val user = authRepository.bindSocial(normalizedPlatform, oauthCode.trim(), state).getOrThrow()
            val localUser = localAccountRepository.getCurrentUser()
            val resolvedUser = user.copy(
                onboardingCompleted = localUser?.onboardingCompleted ?: user.onboardingCompleted,
                localAvatarPath = localUser?.localAvatarPath
            )
            localAccountRepository.saveUser(resolvedUser)
            resolvedUser
        }
    }
}
