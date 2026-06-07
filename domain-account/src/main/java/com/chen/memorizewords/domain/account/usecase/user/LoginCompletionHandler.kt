package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.AuthLoginResult
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.AccountSessionRepository
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.sync.service.SyncFacade
import javax.inject.Inject

class LoginDataSyncError(cause: Throwable? = null) : Throwable(
    message = "Login succeeded but data sync failed",
    cause = cause
)

class LoginCompletionHandler @Inject constructor(
    private val localAccountRepository: LocalAccountRepository,
    private val accountSessionRepository: AccountSessionRepository,
    private val syncFacade: SyncFacade
) {
    suspend fun complete(loginResult: AuthLoginResult): User {
        val user = loginResult.user
        accountSessionRepository.saveSession(loginResult.session)
        localAccountRepository.saveUser(user)
        syncFacade.syncAfterLogin()
            .getOrElse { throw LoginDataSyncError(it) }
        return user
    }
}
