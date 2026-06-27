package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

class FusionPhoneLoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val loginCompletionHandler: LoginCompletionHandler
) {
    suspend operator fun invoke(
        verifyToken: String,
        cancelDeletion: Boolean = false
    ): Result<User> {
        return runCatching {
            if (verifyToken.isBlank()) throw LoginError.EmptyOauthCode()
            val loginResult = authRepository.loginByFusionVerifyToken(
                verifyToken.trim(),
                cancelDeletion = cancelDeletion
            ).getOrThrow()
            loginCompletionHandler.complete(loginResult)
        }
    }
}
