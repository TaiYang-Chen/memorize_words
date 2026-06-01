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

class LoginUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(phoneNumber: String, password: String): Result<User> {
        if (phoneNumber.isBlank()) return Result.failure(LoginError.EmptyPhone())
        if (password.isBlank()) return Result.failure(LoginError.EmptyPassword())
        return repo.login(phoneNumber.trim(), password)
    }
}
