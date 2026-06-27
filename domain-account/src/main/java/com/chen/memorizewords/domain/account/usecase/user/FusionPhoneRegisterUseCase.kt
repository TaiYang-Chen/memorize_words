package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

class FusionPhoneRegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val loginCompletionHandler: LoginCompletionHandler
) {
    suspend operator fun invoke(verifyToken: String, password: String): Result<User> {
        return runCatching {
            if (verifyToken.isBlank()) throw LoginError.EmptyPhone()
            if (password.isBlank()) throw LoginError.EmptyPassword()
            val loginResult = authRepository.registerByFusionVerifyToken(
                verifyToken = verifyToken.trim(),
                password = password
            ).getOrThrow()
            loginCompletionHandler.complete(loginResult)
        }
    }
}
