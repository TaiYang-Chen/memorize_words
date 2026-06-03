package com.chen.memorizewords.domain.account.usecase.user
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

sealed class LoginError : Throwable() {
    class EmptyPhone : LoginError()
    class EmptyPassword : LoginError()
    class EmptySmsCode : LoginError()
    class EmptyOauthCode : LoginError()
}

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val loginCompletionHandler: LoginCompletionHandler
) {
    suspend operator fun invoke(phoneNumber: String, password: String): Result<User> {
        return runCatching {
            if (phoneNumber.isBlank()) throw LoginError.EmptyPhone()
            if (password.isBlank()) throw LoginError.EmptyPassword()
            val loginResult = authRepository.loginByPassword(
                phoneNumber = phoneNumber.trim(),
                password = password
            ).getOrThrow()
            loginCompletionHandler.complete(loginResult)
        }
    }
}
