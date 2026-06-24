package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.AuthLoginResult
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.AccountSessionRepository
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.sync.service.SyncFacade
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

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
        val previousUser = localAccountRepository.getCurrentUser()
        val user = loginResult.user.copy(
            localAvatarPath = previousUser?.takeIf {
                it.userId == loginResult.user.userId && it.avatarUrl == loginResult.user.avatarUrl
            }?.localAvatarPath
        )
        accountSessionRepository.saveSession(loginResult.session)
        localAccountRepository.saveUser(user)
        syncAfterLoginWithRetry()
        return user
    }

    private suspend fun syncAfterLoginWithRetry() {
        var lastFailure: Throwable? = null
        repeat(MAX_POST_LOGIN_SYNC_ATTEMPTS) { attempt ->
            val result = try {
                syncFacade.syncAfterLogin()
            } catch (cancelled: CancellationException) {
                throw cancelled
            }

            result
                .onSuccess { return }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    lastFailure = failure
                }

            if (attempt < POST_LOGIN_SYNC_RETRY_DELAYS_MS.size) {
                delay(POST_LOGIN_SYNC_RETRY_DELAYS_MS[attempt])
            }
        }
        throw LoginDataSyncError(lastFailure)
    }

    private companion object {
        val POST_LOGIN_SYNC_RETRY_DELAYS_MS = longArrayOf(300L, 600L, 1_200L)
        const val MAX_POST_LOGIN_SYNC_ATTEMPTS = 1 + 3
    }
}
