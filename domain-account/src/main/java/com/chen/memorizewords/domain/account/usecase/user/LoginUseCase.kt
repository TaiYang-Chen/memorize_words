package com.chen.memorizewords.domain.account.usecase.user
import com.chen.memorizewords.domain.account.model.classifyValidAuthIdentifier
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

sealed class LoginError : Throwable() {
    class EmptyIdentifier : LoginError()
    class EmptyAccount : LoginError()
    class InvalidAccount : LoginError()
    class InvalidIdentifier : LoginError()
    class EmptyPhone : LoginError()
    class InvalidPhone : LoginError()
    class EmptyEmail : LoginError()
    class EmptyPassword : LoginError()
    class EmptySmsCode : LoginError()
    class EmptyVerifyToken : LoginError()
    class EmptyOauthCode : LoginError()
    class AccountDeletionPending(override val message: String?) : LoginError()
}

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val loginCompletionHandler: LoginCompletionHandler
) {
    suspend operator fun invoke(
        identifier: String,
        password: String,
        cancelDeletion: Boolean = false
    ): Result<User> {
        return runCatching {
            if (identifier.isBlank()) throw LoginError.EmptyIdentifier()
            if (password.isBlank()) throw LoginError.EmptyPassword()
            val authIdentifier = classifyValidAuthIdentifier(identifier)
                ?: throw LoginError.InvalidIdentifier()
            val loginResult = authRepository.loginByPassword(
                identifier = authIdentifier.value,
                identifierType = authIdentifier.type,
                password = password,
                cancelDeletion = cancelDeletion
            ).getOrThrow()
            loginCompletionHandler.complete(loginResult)
        }
    }
}
