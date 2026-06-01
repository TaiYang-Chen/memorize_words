package com.chen.memorizewords.data.sync.repository.sync

import android.content.Context
import androidx.work.WorkManager
import com.chen.memorizewords.domain.sync.PostLoginBootstrapResetter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSyncPostLoginBootstrapResetter @Inject constructor(
    @ApplicationContext context: Context,
    private val postLoginBootstrapStateStore: PostLoginBootstrapStateStore
) : PostLoginBootstrapResetter {

    private val appContext = context.applicationContext

    override fun resetPostLoginBootstrap() {
        runCatching {
            WorkManager.getInstance(appContext)
                .cancelUniqueWork(SyncWorkConstants.WORK_POST_LOGIN_BOOTSTRAP)
        }
        postLoginBootstrapStateStore.reset()
    }
}
