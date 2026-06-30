package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

class PhoneCodeRegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val loginCompletionHandler: LoginCompletionHandler
) {
    suspend operator fun invoke(phone: String, verifyToken: String): Result<User> {
        return runCatching {
            val normalizedPhone = phone.trim()
            if (normalizedPhone.isBlank()) throw LoginError.EmptyPhone()
            if (!PHONE_PATTERN.matches(normalizedPhone)) throw LoginError.InvalidPhone()
            if (verifyToken.isBlank()) throw LoginError.EmptyVerifyToken()
            val loginResult = authRepository.registerByPhoneCode(
                phone = normalizedPhone,
                verifyToken = verifyToken.trim()
            ).getOrThrow()
            loginCompletionHandler.complete(loginResult)
        }
    }

    private companion object {
        val PHONE_PATTERN = "^1[3-9]\\d{9}$".toRegex()
    }
}
