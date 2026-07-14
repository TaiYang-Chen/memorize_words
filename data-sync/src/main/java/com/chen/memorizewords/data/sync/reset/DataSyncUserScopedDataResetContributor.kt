package com.chen.memorizewords.data.sync.reset

import android.content.Context
import androidx.work.WorkManager
import com.chen.memorizewords.data.sync.local.mmkv.checkin.CheckInConfigDataSource
import com.chen.memorizewords.data.sync.local.mmkv.download.UpdateDownloadStore
import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.sync.repository.sync.FailureQueueResetter
import com.chen.memorizewords.data.sync.repository.sync.SyncWorkConstants
import com.chen.memorizewords.domain.account.UserScopedDataResetContributor
import com.chen.memorizewords.domain.sync.repository.HomeStartupSnapshotRepository
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSyncUserScopedDataResetContributor @Inject constructor(
    @ApplicationContext context: Context,
    private val syncOutboxDao: SyncOutboxDao,
    private val failureQueueResetter: FailureQueueResetter,
    private val checkInConfigDataSource: CheckInConfigDataSource,
    private val updateDownloadStore: UpdateDownloadStore,
    private val homeStartupSnapshotRepository: HomeStartupSnapshotRepository
) : UserScopedDataResetContributor {
    override val resetPriority: Int = Int.MIN_VALUE

    private val appContext = context.applicationContext

    override suspend fun clearUserScopedData() {
        failureQueueResetter.reset()
        cancelUserScopedWork()
        syncOutboxDao.deleteAll()
        checkInConfigDataSource.clearUserScopedState()
        updateDownloadStore.clear()
        homeStartupSnapshotRepository.clearSnapshot()
    }

    private fun cancelUserScopedWork() {
        val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull() ?: return
        listOf(
            SyncWorkConstants.TAG_SYNC_OUTBOX_DRAIN,
            SyncWorkConstants.TAG_SYNC_OUTBOX_IMMEDIATE_DRAIN,
            SyncWorkConstants.TAG_SYNC_OUTBOX_RETRY,
            SyncWorkConstants.TAG_SYNC_OUTBOX_PERIODIC,
            SyncWorkConstants.TAG_POST_LOGIN_BOOTSTRAP,
            SyncWorkConstants.TAG_ADD_MY_WORD_BOOK,
            SyncWorkConstants.TAG_STUDY_PLAN_SYNC,
            SyncWorkConstants.TAG_FAVORITE_SYNC,
            TAG_WORD_BOOK_DOWNLOAD,
            LEGACY_WORK_WORD_BOOK_BOOTSTRAP,
            TAG_CURRENT_WORD_BOOK_UPDATE
        ).forEach(workManager::cancelAllWorkByTag)
        listOf(
            SyncWorkConstants.WORK_SYNC_OUTBOX_DRAIN,
            SyncWorkConstants.WORK_SYNC_OUTBOX_IMMEDIATE_DRAIN,
            SyncWorkConstants.WORK_SYNC_OUTBOX_RETRY,
            SyncWorkConstants.WORK_SYNC_OUTBOX_PERIODIC,
            SyncWorkConstants.WORK_POST_LOGIN_BOOTSTRAP,
            SyncWorkConstants.UNIQUE_DATA_BOOTSTRAP,
            LEGACY_WORK_WORD_BOOK_BOOTSTRAP
        ).forEach(workManager::cancelUniqueWork)
    }
}

private const val TAG_WORD_BOOK_DOWNLOAD = "wordbook_download"
private const val TAG_CURRENT_WORD_BOOK_UPDATE = "current_wordbook_update"
private const val LEGACY_WORK_WORD_BOOK_BOOTSTRAP = "bootstrap_wordbook_data"

@Module
@InstallIn(SingletonComponent::class)
abstract class DataSyncUserScopedDataResetModule {
    @Binds
    @IntoSet
    abstract fun bindUserScopedDataResetContributor(
        impl: DataSyncUserScopedDataResetContributor
    ): UserScopedDataResetContributor
}
