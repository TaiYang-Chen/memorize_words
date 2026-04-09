package com.chen.memorizewords.domain.usecase.user

import com.chen.memorizewords.domain.model.user.User
import com.chen.memorizewords.domain.repository.user.AuthRepository
import javax.inject.Inject

class BindSocialUseCase @Inject constructor(
    private val repo: AuthRepository
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
        return repo.bindSocial(normalizedPlatform, oauthCode, state)
    }
}
