package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val loginCompletionHandler: LoginCompletionHandler
) {
    suspend operator fun invoke(phoneNumber: String, password: String): Result<User> {
        return runCatching {
            require(phoneNumber.isNotBlank()) { "Phone number is required" }
            require(password.isNotBlank()) { "Password is required" }
            val loginResult = authRepository.register(phoneNumber.trim(), password).getOrThrow()
            loginCompletionHandler.complete(loginResult)
        }
    }
}

