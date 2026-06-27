package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.UserRepository
import javax.inject.Inject

class BindPhoneUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(verifyToken: String): Result<User> {
        return runCatching {
            val token = verifyToken.trim()
            if (token.isBlank()) throw LoginError.EmptyOauthCode()
            userRepository.bindPhoneByFusionVerifyToken(token).getOrThrow()
        }
    }
}
