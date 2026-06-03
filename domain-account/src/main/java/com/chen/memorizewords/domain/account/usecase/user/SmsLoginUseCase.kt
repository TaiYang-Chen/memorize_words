package com.chen.memorizewords.domain.account.usecase.user
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

class SmsLoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val loginCompletionHandler: LoginCompletionHandler
) {
    suspend operator fun invoke(phone: String, code: String): Result<User> {
        return runCatching {
            if (phone.isBlank()) throw LoginError.EmptyPhone()
            if (code.isBlank()) throw LoginError.EmptySmsCode()
            val loginResult = authRepository.loginBySms(
                phone = phone.trim(),
                code = code.trim()
            ).getOrThrow()
            loginCompletionHandler.complete(loginResult)
        }
    }
}
