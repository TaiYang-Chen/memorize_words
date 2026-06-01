package com.chen.memorizewords.data.account.session

import android.content.Context
import com.chen.memorizewords.data.account.local.mmkv.auth.AuthLocalDataSource
import com.chen.memorizewords.domain.account.auth.SessionKickoutNotifier
import com.chen.memorizewords.domain.sync.PostLoginBootstrapResetter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAuthSessionCleaner @Inject constructor(
    @ApplicationContext context: Context,
    private val sessionLocalDataSource: SessionLocalDataSource,
    private val authLocalDataSource: AuthLocalDataSource,
    private val sessionKickoutNotifier: SessionKickoutNotifier,
    private val localUserDataOwnerDataSource: LocalUserDataOwnerDataSource,
    private val postLoginBootstrapResetter: PostLoginBootstrapResetter
) : LocalAuthStateCleaner {

    private val isClearing = AtomicBoolean(false)
    private val appContext = context.applicationContext

    override suspend fun clearLocalAuthState(notifyKickout: Boolean) {
        if (!isClearing.compareAndSet(false, true)) {
            return
        }
        try {
            postLoginBootstrapResetter.resetPostLoginBootstrap()
            authLocalDataSource.getUserId()?.let(localUserDataOwnerDataSource::saveOwnerUserId)
            sessionLocalDataSource.clear()
            authLocalDataSource.clear()
            if (notifyKickout) {
                sessionKickoutNotifier.notifyKickout()
            }
        } finally {
            isClearing.set(false)
        }
    }
}
