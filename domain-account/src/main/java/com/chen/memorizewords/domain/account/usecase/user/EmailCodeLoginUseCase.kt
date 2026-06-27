package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

class EmailCodeLoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val loginCompletionHandler: LoginCompletionHandler
) {
    suspend operator fun invoke(
        email: String,
        code: String,
        cancelDeletion: Boolean = false
    ): Result<User> {
        return runCatching {
            if (email.isBlank()) throw LoginError.EmptyEmail()
            if (code.isBlank()) throw LoginError.EmptySmsCode()
            val loginResult = authRepository.loginByEmailCode(
                email = email.trim(),
                code = code.trim(),
                cancelDeletion = cancelDeletion
            ).getOrThrow()
            loginCompletionHandler.complete(loginResult)
        }
    }
}
