package com.chen.memorizewords.domain.usecase.user

import com.chen.memorizewords.domain.repository.user.AuthRepository
import javax.inject.Inject

class ChangePasswordUseCase @Inject constructor(
    private val repo: AuthRepository
) {
    suspend operator fun invoke(oldPassword: String, newPassword: String): Result<Unit> {
        if (oldPassword.isBlank()) {
            return Result.failure(IllegalArgumentException("Old password is required"))
        }
        if (newPassword.isBlank()) {
            return Result.failure(IllegalArgumentException("New password is required"))
        }
        if (newPassword.length < 6) {
            return Result.failure(IllegalArgumentException("New password must be at least 6 characters"))
        }
        if (oldPassword == newPassword) {
            return Result.failure(IllegalArgumentException("New password must be different from old password"))
        }
        return repo.changePassword(oldPassword, newPassword)
    }
}
