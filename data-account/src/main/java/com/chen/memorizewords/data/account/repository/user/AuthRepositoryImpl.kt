package com.chen.memorizewords.data.account.repository.user

import com.chen.memorizewords.data.account.local.mmkv.auth.AuthLocalDataSource
import com.chen.memorizewords.data.account.remote.user.AuthRemoteDataSource
import com.chen.memorizewords.data.account.session.AuthSession
import com.chen.memorizewords.domain.account.auth.AuthStateProvider
import com.chen.memorizewords.data.account.session.LocalUserDataOwnerDataSource
import com.chen.memorizewords.data.account.session.LocalAuthStateCleaner
import com.chen.memorizewords.data.account.session.SessionManager
import com.chen.memorizewords.data.account.session.UserDataCleaner
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingPhase
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.wordbook.repository.onboarding.OnboardingRepository
import com.chen.memorizewords.domain.sync.repository.SyncRepository
import com.chen.memorizewords.domain.account.model.user.SmsCodeMeta
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import com.chen.memorizewords.domain.account.repository.user.LogoutDataLossRiskException
import com.chen.memorizewords.data.account.remoteapi.api.auth.LoginRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.RegisterRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.SendSmsCodeRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.ChangePasswordRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.BindSocialRequest
import com.chen.memorizewords.data.account.remoteapi.dto.OnboardingStateDto
import com.chen.memorizewords.data.account.remoteapi.dto.LoginResponseDto
import com.chen.memorizewords.data.account.remoteapi.dto.ProfileDto
import com.chen.memorizewords.domain.sync.SyncDrainOutcome
import com.chen.memorizewords.domain.sync.SyncLogoutFlusher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val remote: AuthRemoteDataSource,
    private val authLocal: AuthLocalDataSource,
    private val sessionManager: SessionManager,
    private val authStateProvider: AuthStateProvider,
    private val localUserDataOwnerDataSource: LocalUserDataOwnerDataSource,
    private val localAuthStateCleaner: LocalAuthStateCleaner,
    private val userDataCleaner: UserDataCleaner,
    private val syncRepository: SyncRepository,
    private val syncLogoutFlusher: SyncLogoutFlusher,
    private val onboardingRepository: OnboardingRepository
) : AuthRepository {

    override suspend fun login(
        phoneNumber: String,
        password: String
    ): Result<User> = runCatching {
        withContext(Dispatchers.IO) {
            val request = LoginRequest(
                loginMethod = "password",
                emailOrPhone = phoneNumber,
                phone = phoneNumber,
                password = password
            )
            val response = remote.login(request).getOrThrow()
            saveSessionAndUser(response)
        }
    }

    override suspend fun sendLoginSmsCode(phone: String): Result<SmsCodeMeta> = runCatching {
        withContext(Dispatchers.IO) {
            val response = remote.sendLoginSmsCode(
                SendSmsCodeRequest(phone = phone)
            ).getOrThrow()

            SmsCodeMeta(
                expireSeconds = response.expireSeconds,
                resendIntervalSeconds = response.resendIntervalSeconds
            )
        }
    }

    override suspend fun loginBySms(phone: String, code: String): Result<User> = runCatching {
        withContext(Dispatchers.IO) {
            val request = LoginRequest(
                loginMethod = "sms",
                phone = phone,
                smsCode = code
            )
            val response = remote.loginBySms(request).getOrThrow()
            saveSessionAndUser(response)
        }
    }

    override suspend fun loginByWechat(oauthCode: String, state: String?): Result<User> = runCatching {
        withContext(Dispatchers.IO) {
            val request = LoginRequest(
                loginMethod = "wechat",
                oauthCode = oauthCode,
                platform = "wechat",
                state = state
            )
            val response = remote.loginByWechat(request).getOrThrow()
            saveSessionAndUser(response)
        }
    }

    override suspend fun loginByQq(oauthCode: String, state: String?): Result<User> = runCatching {
        withContext(Dispatchers.IO) {
            val request = LoginRequest(
                loginMethod = "qq",
                oauthCode = oauthCode,
                platform = "qq",
                state = state
            )
            val response = remote.loginByQq(request).getOrThrow()
            saveSessionAndUser(response)
        }
    }

    override suspend fun register(
        phoneNumber: String,
        password: String
    ): Result<User> = withContext(Dispatchers.IO) {
        try {
            val request = RegisterRequest(
                phone = phoneNumber,
                password = password
            )
            val response = remote.register(request)
                .getOrElse { return@withContext Result.failure(it) }

            Result.success(saveSessionAndUser(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun isLoggedIn(): Boolean {
        return authStateProvider.isAuthenticated()
    }

    override suspend fun getCurrentUser(): User? = withContext(Dispatchers.IO) {
        authLocal.getUser()
    }

    override fun getUserFlow(): Flow<User?> = authLocal.getUserFlow()

    override suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            remote.changePassword(
                ChangePasswordRequest(
                    oldPassword = oldPassword,
                    newPassword = newPassword
                )
            )
        }

    override suspend fun bindSocial(
        platform: String,
        oauthCode: String,
        state: String?
    ): Result<User> = runCatching {
        withContext(Dispatchers.IO) {
            val profile = remote.bindSocial(
                BindSocialRequest(
                    platform = platform,
                    oauthCode = oauthCode,
                    state = state
                )
            ).getOrThrow()
            val user = mapProfile(profile)
            authLocal.saveUser(user)
            user
        }
    }

    override suspend fun logout(force: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        if (!force && authStateProvider.isAuthenticated()) {
            bestEffortFlushPendingSync()
            if (shouldAbortLogoutAfterFlush(force, syncLogoutFlusher.getPendingCount())) {
                return@withContext Result.failure(LogoutDataLossRiskException())
            }
        }
        val remoteResult = runCatching {
            remote.logout().getOrThrow()
            Unit
        }
        clearLocalAuthAndUserData()
        remoteResult
    }

    override suspend fun deleteAccount(): Result<Unit> = withContext(Dispatchers.IO) {
        val result = runCatching {
            remote.deleteAccount().getOrThrow()
            Unit
        }
        result.onSuccess {
            clearLocalAuthAndUserData()
        }
        result
    }

    private suspend fun saveSessionAndUser(response: LoginResponseDto): User {
        val user = response.user
        val localUser = User(
            userId = user.id,
            email = user.email,
            nickname = user.nickname,
            gender = user.gender,
            avatarUrl = user.avatarUrl,
            phone = user.phone,
            qq = user.qq,
            wechat = user.wechat,
            emailVerified = user.emailVerified
        )

        when (
            resolveLoginLocalDataAction(
                isAuthenticated = authStateProvider.isAuthenticated(),
                authenticatedUserId = authLocal.getUserId(),
                retainedOwnerUserId = localUserDataOwnerDataSource.getOwnerUserId(),
                incomingUserId = localUser.userId,
                hasUnsyncedUserData = hasBlockingPendingSyncData()
            )
        ) {
            LoginLocalDataAction.Block -> throw LogoutDataLossRiskException()
            LoginLocalDataAction.Clear -> userDataCleaner.clearUserLearningData()
            LoginLocalDataAction.Keep -> Unit
        }

        sessionManager.save(
            AuthSession(
                accessToken = response.token,
                refreshToken = response.refreshToken,
                expiresAt = System.currentTimeMillis() + response.expiresIn * 1000L
            )
        )
        authLocal.saveUser(localUser)
        localUserDataOwnerDataSource.saveOwnerUserId(localUser.userId)
        onboardingRepository.initializeSnapshotForUser(
            userId = localUser.userId,
            snapshot = response.onboarding?.toDomain()
        )
        syncRepository.startPostLoginBootstrap()
        return localUser
    }

    private fun mapProfile(profile: ProfileDto): User {
        return User(
            userId = profile.userId,
            email = profile.email,
            nickname = profile.nickname,
            gender = profile.gender,
            avatarUrl = profile.avatarUrl,
            phone = profile.phone,
            qq = profile.qq,
            wechat = profile.wechat,
            emailVerified = profile.emailVerified
        )
    }

    private suspend fun clearAuthenticatedSessionPreservingUserData() {
        localAuthStateCleaner.clearLocalAuthState(notifyKickout = false)
    }

    private suspend fun clearLocalAuthAndUserData() {
        clearAuthenticatedSessionPreservingUserData()
        userDataCleaner.clearUserLearningData()
        localUserDataOwnerDataSource.clearOwnerUserId()
    }

    private suspend fun hasBlockingPendingSyncData(): Boolean {
        return hasBlockingPendingSyncData(
            pendingSyncCount = syncLogoutFlusher.getPendingCount()
        )
    }

    private suspend fun bestEffortFlushPendingSync() {
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
}

internal fun hasBlockingPendingSyncData(
    pendingSyncCount: Int
): Boolean {
    return pendingSyncCount > 0
}

internal fun shouldAbortLogoutAfterFlush(
    force: Boolean,
    pendingSyncCount: Int
): Boolean {
    return !force && hasBlockingPendingSyncData(pendingSyncCount)
}

internal enum class LoginLocalDataAction {
    Keep,
    Clear,
    Block
}

internal fun resolveLoginLocalDataAction(
    isAuthenticated: Boolean,
    authenticatedUserId: Long?,
    retainedOwnerUserId: Long?,
    incomingUserId: Long,
    hasUnsyncedUserData: Boolean
): LoginLocalDataAction {
    return when {
        shouldBlockAuthenticatedAccountSwitchBeforeLogin(
            isAuthenticated = isAuthenticated,
            authenticatedUserId = authenticatedUserId,
            incomingUserId = incomingUserId,
            hasUnsyncedUserData = hasUnsyncedUserData
        ) -> LoginLocalDataAction.Block

        shouldClearLocalUserDataBeforeLogin(
            isAuthenticated = isAuthenticated,
            authenticatedUserId = authenticatedUserId,
            retainedOwnerUserId = retainedOwnerUserId,
            incomingUserId = incomingUserId
        ) -> LoginLocalDataAction.Clear

        else -> LoginLocalDataAction.Keep
    }
}

internal fun shouldBlockAuthenticatedAccountSwitchBeforeLogin(
    isAuthenticated: Boolean,
    authenticatedUserId: Long?,
    incomingUserId: Long,
    hasUnsyncedUserData: Boolean
): Boolean {
    return isAuthenticated &&
        authenticatedUserId != null &&
        authenticatedUserId != incomingUserId &&
        hasUnsyncedUserData
}

internal fun shouldClearLocalUserDataBeforeLogin(
    isAuthenticated: Boolean,
    authenticatedUserId: Long?,
    retainedOwnerUserId: Long?,
    incomingUserId: Long
): Boolean {
    return if (isAuthenticated) {
        authenticatedUserId != null && authenticatedUserId != incomingUserId
    } else {
        retainedOwnerUserId != null && retainedOwnerUserId != incomingUserId
    }
}

private const val MAX_LOGOUT_SYNC_DRAIN_ROUNDS = 5

private fun OnboardingStateDto.toDomain(): OnboardingSnapshot {
    return OnboardingSnapshot(
        phase = runCatching { OnboardingPhase.valueOf(phase) }
            .getOrDefault(OnboardingPhase.NEEDS_WORD_BOOK),
        selectedWordBookId = selectedWordBookId,
        revision = revision,
        updatedAt = updatedAt,
        completedAt = completedAt
    )
}
