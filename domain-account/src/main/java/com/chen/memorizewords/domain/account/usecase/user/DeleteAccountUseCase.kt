package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.repository.AccountSessionRepository
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.account.repository.UserScopedDataCleaner
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

class DeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountSessionRepository: AccountSessionRepository,
    private val localAccountRepository: LocalAccountRepository,
    private val userScopedDataCleaner: UserScopedDataCleaner
) {
    suspend operator fun invoke(): Result<Unit> {
        val remoteResult = authRepository.deleteAccountRemote()
        remoteResult.onSuccess {
            accountSessionRepository.clearSession()
            localAccountRepository.clearUser()
            userScopedDataCleaner.clearUserScopedData()
        }
        return remoteResult
    }
}
