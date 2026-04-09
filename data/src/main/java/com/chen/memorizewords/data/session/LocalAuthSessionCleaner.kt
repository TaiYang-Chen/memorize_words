package com.chen.memorizewords.data.session

import android.content.Context
import androidx.work.WorkManager
import com.chen.memorizewords.data.local.mmkv.auth.AuthLocalDataSource
import com.chen.memorizewords.data.repository.sync.PostLoginBootstrapStateStore
import com.chen.memorizewords.data.repository.sync.SyncWorkConstants
import com.chen.memorizewords.domain.auth.SessionKickoutNotifier
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
    private val postLoginBootstrapStateStore: PostLoginBootstrapStateStore
) : LocalAuthStateCleaner {

    private val isClearing = AtomicBoolean(false)
    private val appContext = context.applicationContext

    override suspend fun clearLocalAuthState(notifyKickout: Boolean) {
        if (!isClearing.compareAndSet(false, true)) {
            return
        }
        try {
            runCatching {
                WorkManager.getInstance(appContext)
                    .cancelUniqueWork(SyncWorkConstants.WORK_POST_LOGIN_BOOTSTRAP)
            }
            postLoginBootstrapStateStore.reset()
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
