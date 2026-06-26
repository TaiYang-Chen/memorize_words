package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

class PhoneRegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val loginCompletionHandler: LoginCompletionHandler
) {
    suspend operator fun invoke(phone: String, smsCode: String, password: String): Result<User> {
        return runCatching {
            if (phone.isBlank()) throw LoginError.EmptyPhone()
            if (smsCode.isBlank()) throw LoginError.EmptySmsCode()
            if (password.isBlank()) throw LoginError.EmptyPassword()
            val loginResult = authRepository.registerByPhone(
                phone = phone.trim(),
                smsCode = smsCode.trim(),
                password = password
            ).getOrThrow()
            loginCompletionHandler.complete(loginResult)
        }
    }
}
