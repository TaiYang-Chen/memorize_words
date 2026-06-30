package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

class EmailCodeRegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val loginCompletionHandler: LoginCompletionHandler
) {
    suspend operator fun invoke(email: String, code: String): Result<User> {
        return runCatching {
            if (email.isBlank()) throw LoginError.EmptyEmail()
            if (code.isBlank()) throw LoginError.EmptySmsCode()
            val loginResult = authRepository.registerByEmailCode(
                email = email.trim(),
                emailCode = code.trim()
            ).getOrThrow()
            loginCompletionHandler.complete(loginResult)
        }
    }
}
