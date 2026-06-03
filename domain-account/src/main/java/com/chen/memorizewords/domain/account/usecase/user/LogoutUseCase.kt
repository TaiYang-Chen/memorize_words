package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.LogoutOutcome
import com.chen.memorizewords.domain.account.policy.LogoutDataLossPolicy
import com.chen.memorizewords.domain.account.repository.AccountSessionRepository
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.account.repository.UserDataOwnerRepository
import com.chen.memorizewords.domain.account.repository.UserScopedDataCleaner
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import com.chen.memorizewords.domain.account.repository.user.LogoutDataLossRiskException
import com.chen.memorizewords.domain.sync.SyncDrainOutcome
import com.chen.memorizewords.domain.sync.SyncLogoutFlusher
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val localAccountRepository: LocalAccountRepository,
    private val accountSessionRepository: AccountSessionRepository,
    private val userDataOwnerRepository: UserDataOwnerRepository,
    private val userScopedDataCleaner: UserScopedDataCleaner,
    private val syncLogoutFlusher: SyncLogoutFlusher,
    private val logoutDataLossPolicy: LogoutDataLossPolicy
) {
    suspend operator fun invoke(force: Boolean = false): Result<LogoutOutcome> = runCatching {
        if (!force && localAccountRepository.isLoggedIn()) {
            drainPendingSync()
            val pendingCount = syncLogoutFlusher.getPendingCount()
            if (logoutDataLossPolicy.shouldAbortAfterFlush(force, pendingCount)) {
                throw LogoutDataLossRiskException()
            }
        }

        val remoteResult = authRepository.logoutRemote()
        clearLocalAccountState()

        remoteResult.fold(
            onSuccess = { LogoutOutcome.Success },
            onFailure = { cause -> LogoutOutcome.LocalClearedRemoteFailed(cause) }
        )
    }

    private suspend fun drainPendingSync() {
        repeat(MAX_LOGOUT_SYNC_DRAIN_ROUNDS) {
            if (syncLogoutFlusher.getPendingCount() <= 0) {
                return
            }

            when (syncLogoutFlusher.drainOnce()) {
                SyncDrainOutcome.EMPTY -> return
                SyncDrainOutcome.DRAINED -> Unit
                SyncDrainOutcome.RETRY_NEEDED -> return
            }
        }
    }

    private suspend fun clearLocalAccountState() {
        accountSessionRepository.clearSession()
        localAccountRepository.clearUser()
        userScopedDataCleaner.clearUserScopedData()
        userDataOwnerRepository.clearOwnerUserId()
    }

    private companion object {
        const val MAX_LOGOUT_SYNC_DRAIN_ROUNDS = 5
    }
}

