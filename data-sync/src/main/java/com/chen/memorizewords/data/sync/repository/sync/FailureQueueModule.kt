package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.core.common.coroutines.ApplicationScope
import com.chen.memorizewords.core.common.coroutines.DirectSyncLauncher
import com.chen.memorizewords.domain.sync.PendingCheckInSyncQuery
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
abstract class FailureQueueModule {
    @Binds
    @Singleton
    abstract fun bindFailedRequestRecorder(impl: DefaultFailedRequestRecorder): FailedRequestRecorder

    @Binds
    @Singleton
    abstract fun bindFailedSyncScheduler(impl: FailedSyncWorkScheduler): FailedSyncScheduler

    @Binds
    @Singleton
    abstract fun bindNetworkRecoveryNotifier(impl: DefaultNetworkRecoveryNotifier): NetworkRecoveryNotifier

    @Binds
    @Singleton
    abstract fun bindLatestSyncRequestCoordinator(
        impl: DefaultLatestSyncRequestCoordinator
    ): LatestSyncRequestCoordinator

    @Binds
    @Singleton
    abstract fun bindLatestSyncEventAccess(
        impl: FailedSyncEventStore
    ): LatestSyncEventAccess

    @Binds
    @Singleton
    abstract fun bindPendingCheckInSyncQuery(
        impl: PendingCheckInSyncQueryImpl
    ): PendingCheckInSyncQuery
}

@Module
@InstallIn(SingletonComponent::class)
object DirectSyncLauncherModule {
    @Provides
    @Singleton
    fun provideDirectSyncLauncher(
        @ApplicationScope applicationScope: CoroutineScope
    ): DirectSyncLauncher = DirectSyncLauncher(applicationScope)
}
