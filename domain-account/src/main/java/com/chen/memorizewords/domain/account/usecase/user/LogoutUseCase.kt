package com.chen.memorizewords.domain.account.usecase.user
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(force: Boolean = false): Result<Unit> = repository.logout(force)
}
