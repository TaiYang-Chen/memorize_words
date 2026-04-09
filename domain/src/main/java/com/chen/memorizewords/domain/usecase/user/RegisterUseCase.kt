package com.chen.memorizewords.domain.usecase.user

import com.chen.memorizewords.domain.model.user.User
import com.chen.memorizewords.domain.repository.user.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(phoneNumber: String, password: String): Result<User> {
        if (phoneNumber.isBlank()) return Result.failure(IllegalArgumentException("手机号不能为空"))
        if (password.length < 6) return Result.failure(IllegalArgumentException("密码长度至少6位"))
        return repo.register(phoneNumber.trim(), password)
    }
}