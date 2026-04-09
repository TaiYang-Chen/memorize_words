package com.chen.memorizewords.domain.usecase.user

import com.chen.memorizewords.domain.model.user.User
import com.chen.memorizewords.domain.repository.user.AuthRepository
import javax.inject.Inject

class SmsLoginUseCase @Inject constructor(
    private val repo: AuthRepository
) {
    suspend operator fun invoke(phone: String, code: String): Result<User> {
        if (phone.isBlank()) return Result.failure(LoginError.EmptyPhone())
        if (code.isBlank()) return Result.failure(LoginError.EmptySmsCode())
        return repo.loginBySms(phone.trim(), code.trim())
    }
}
