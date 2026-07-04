package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.AccountNamePolicy
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val loginCompletionHandler: LoginCompletionHandler
) {
    suspend operator fun invoke(account: String, password: String): Result<User> {
        return runCatching {
            if (account.isBlank()) throw LoginError.EmptyAccount()
            if (password.isBlank()) throw LoginError.EmptyPassword()
            val normalizedAccount = account.trim()
            if (!AccountNamePolicy.isValid(normalizedAccount)) throw LoginError.InvalidAccount()
            val loginResult = authRepository.registerByAccount(
                account = normalizedAccount,
                password = password
            ).getOrThrow()
            loginCompletionHandler.complete(loginResult)
        }
    }
}
