package com.chen.memorizewords.data.account.session

import com.chen.memorizewords.data.account.local.avatar.AvatarLocalDataSource
import com.chen.memorizewords.data.account.local.mmkv.auth.AuthLocalDataSource
import com.chen.memorizewords.domain.account.auth.SessionKickoutNotifier
import com.chen.memorizewords.domain.account.repository.UserScopedDataCleaner
import com.chen.memorizewords.domain.sync.PostLoginBootstrapResetter
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class LocalAuthSessionCleaner @Inject constructor(
    private val sessionLocalDataSource: SessionLocalDataSource,
    private val authLocalDataSource: AuthLocalDataSource,
    private val sessionKickoutNotifier: SessionKickoutNotifier,
    private val userScopedDataCleanerProvider: Provider<UserScopedDataCleaner>,
    private val postLoginBootstrapResetter: PostLoginBootstrapResetter,
    private val avatarLocalDataSource: AvatarLocalDataSource
) : LocalAuthStateCleaner {

    private val isClearing = AtomicBoolean(false)
    override suspend fun clearLocalAuthState(notifyKickout: Boolean) {
        if (!isClearing.compareAndSet(false, true)) {
            return
        }
        try {
            postLoginBootstrapResetter.resetPostLoginBootstrap()
            val avatarPath = authLocalDataSource.getUser()?.localAvatarPath
            sessionLocalDataSource.clear()
            authLocalDataSource.clear()
            userScopedDataCleanerProvider.get().clearUserScopedData()
            avatarLocalDataSource.deleteAvatar(avatarPath)
            if (notifyKickout) {
                sessionKickoutNotifier.notifyKickout()
            }
        } finally {
            isClearing.set(false)
        }
    }
}
