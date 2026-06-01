package com.chen.memorizewords.domain.account.usecase.user
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

class DeleteAccountUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> = repository.deleteAccount()
}
