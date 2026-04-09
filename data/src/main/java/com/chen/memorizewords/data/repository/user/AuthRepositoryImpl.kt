package com.chen.memorizewords.data.repository.user

import com.chen.memorizewords.data.local.mmkv.auth.AuthLocalDataSource
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.remote.user.AuthRemoteDataSource
import com.chen.memorizewords.data.repository.sync.SyncOutboxProcessor
import com.chen.memorizewords.data.session.AuthSession
import com.chen.memorizewords.domain.auth.AuthStateProvider
import com.chen.memorizewords.data.session.LocalUserDataOwnerDataSource
import com.chen.memorizewords.data.session.LocalAuthStateCleaner
import com.chen.memorizewords.data.session.SessionManager
import com.chen.memorizewords.data.session.UserDataCleaner
import com.chen.memorizewords.domain.repository.sync.SyncRepository
import com.chen.memorizewords.domain.model.user.SmsCodeMeta
import com.chen.memorizewords.domain.model.user.User
import com.chen.memorizewords.domain.repository.user.AuthRepository
import com.chen.memorizewords.domain.repository.user.LogoutDataLossRiskException
import com.chen.memorizewords.network.api.auth.LoginRequest
import com.chen.memorizewords.network.api.auth.RegisterRequest
import com.chen.memorizewords.network.api.auth.SendSmsCodeRequest
import com.chen.memorizewords.network.api.auth.ChangePasswordRequest
import com.chen.memorizewords.network.api.auth.BindSocialRequest
import com.chen.memorizewords.network.dto.LoginResponseDto
import com.chen.memorizewords.network.dto.ProfileDto
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
    private val syncOutboxDao: SyncOutboxDao,
    private val syncRepository: SyncRepository,
    private val syncOutboxProcessor: SyncOutboxProcessor
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

        val previousUserId = resolveExistingLocalDataOwnerUserId(
            authenticatedUserId = authLocal.getUserId(),
            retainedOwnerUserId = localUserDataOwnerDataSource.getOwnerUserId()
        )
        if (shouldBlockAccountSwitch(previousUserId, localUser.userId, hasBlockingPendingSyncData())) {
            throw LogoutDataLossRiskException()
        }

        sessionManager.save(
            AuthSession(
                accessToken = response.token,
                refreshToken = response.refreshToken,
                expiresAt = System.currentTimeMillis() + response.expiresIn * 1000L
            )
        )
        if (shouldClearRetainedUserData(previousUserId, localUser.userId)) {
            userDataCleaner.clearUserLearningData()
        }
        authLocal.saveUser(localUser)
        localUserDataOwnerDataSource.saveOwnerUserId(localUser.userId)
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
            pendingSyncCount = syncOutboxDao.getPendingCountValue()
        )
    }

    private suspend fun bestEffortFlushPendingSync() {
        repeat(MAX_LOGOUT_SYNC_DRAIN_ROUNDS) {
            if (syncOutboxDao.getPendingCountValue() <= 0) {
                return
            }
            when (syncOutboxProcessor.drainBatch()) {
                SyncOutboxProcessor.DrainResult.Empty -> return
                SyncOutboxProcessor.DrainResult.Drained -> Unit
                SyncOutboxProcessor.DrainResult.RetryNeeded -> return
            }
        }
    }
}

internal fun hasBlockingPendingSyncData(
    pendingSyncCount: Int
): Boolean {
    return pendingSyncCount > 0
}

internal fun resolveExistingLocalDataOwnerUserId(
    authenticatedUserId: Long?,
    retainedOwnerUserId: Long?
): Long? {
    return authenticatedUserId ?: retainedOwnerUserId
}

internal fun shouldClearRetainedUserData(
    existingOwnerUserId: Long?,
    incomingUserId: Long
): Boolean {
    return existingOwnerUserId != null && existingOwnerUserId != incomingUserId
}

internal fun shouldBlockAccountSwitch(
    previousUserId: Long?,
    incomingUserId: Long,
    hasUnsyncedUserData: Boolean
): Boolean {
    return previousUserId != null &&
        previousUserId != incomingUserId &&
        hasUnsyncedUserData
}

private const val MAX_LOGOUT_SYNC_DRAIN_ROUNDS = 5
