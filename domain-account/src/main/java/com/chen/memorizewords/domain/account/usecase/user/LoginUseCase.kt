package com.chen.memorizewords.domain.account.usecase.user
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

sealed class LoginError : Throwable() {
    class EmptyPhone : LoginError()
    class InvalidPhone : LoginError()
    class EmptyEmail : LoginError()
    class EmptyPassword : LoginError()
    class EmptySmsCode : LoginError()
    class EmptyOauthCode : LoginError()
}

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val loginCompletionHandler: LoginCompletionHandler
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        return runCatching {
            if (email.isBlank()) throw LoginError.EmptyEmail()
            if (password.isBlank()) throw LoginError.EmptyPassword()
            val loginResult = authRepository.loginByPassword(
                phoneNumber = email.trim(),
                password = password
            ).getOrThrow()
            loginCompletionHandler.complete(loginResult)
        }
    }
}
