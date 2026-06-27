package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.AuthLoginResult
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.AccountSessionRepository
import com.chen.memorizewords.domain.account.repository.LoginBootstrapApplier
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.sync.service.SyncFacade
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class LoginDataSyncError(cause: Throwable? = null) : Throwable(
    message = "Login succeeded but data sync failed",
    cause = cause
)

class LoginCompletionHandler @Inject constructor(
    private val localAccountRepository: LocalAccountRepository,
    private val accountSessionRepository: AccountSessionRepository,
    private val loginBootstrapApplier: LoginBootstrapApplier,
    private val syncFacade: SyncFacade
) {
    suspend fun complete(loginResult: AuthLoginResult): User {
        val previousUser = localAccountRepository.getCurrentUser()
        val user = loginResult.user.copy(
            localAvatarPath = previousUser?.takeIf {
                it.userId == loginResult.user.userId && it.avatarUrl == loginResult.user.avatarUrl
            }?.localAvatarPath
        )
        accountSessionRepository.saveSession(loginResult.session)
        localAccountRepository.saveUser(user)
        applyBootstrapIfPresent(loginResult)
        syncFacade.startPostLoginBootstrap()
        return user
    }

    private suspend fun applyBootstrapIfPresent(loginResult: AuthLoginResult) {
        try {
            loginBootstrapApplier.apply(loginResult.bootstrap)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Background bootstrap is still scheduled below; local snapshot failure must not keep login open.
        }
    }
}
