package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.UserRepository
import javax.inject.Inject

class BindEmailUseCase @Inject constructor(
    private val repo: UserRepository
) {
    suspend operator fun invoke(email: String, emailCode: String): Result<User> = runCatching {
        if (email.isBlank()) throw LoginError.EmptyEmail()
        if (emailCode.isBlank()) throw LoginError.EmptySmsCode()
        repo.bindEmail(
            email = email.trim(),
            emailCode = emailCode.trim()
        ).getOrThrow()
    }
}
