package com.chen.memorizewords.data.sync.di

import com.chen.memorizewords.data.sync.repository.download.DownloadRepositoryImpl
import com.chen.memorizewords.data.sync.repository.sync.DataSyncConflictPolicy
import com.chen.memorizewords.data.sync.repository.sync.DataSyncLogoutFlusher
import com.chen.memorizewords.data.sync.repository.sync.DataSyncOutboxReader
import com.chen.memorizewords.data.sync.repository.sync.DataSyncOutboxWriter
import com.chen.memorizewords.data.sync.repository.sync.DataSyncPostLoginBootstrapResetter
import com.chen.memorizewords.data.sync.repository.membership.MembershipRepositoryImpl
import com.chen.memorizewords.data.sync.repository.sync.SyncRepositoryImpl
import com.chen.memorizewords.data.sync.repository.sync.SyncAuthenticatedRequestSuccessReporter
import com.chen.memorizewords.data.sync.repository.sync.SyncOutboxDrainScheduler
import com.chen.memorizewords.data.sync.repository.sync.SyncOutboxRetryWaitResumer
import com.chen.memorizewords.data.sync.repository.sync.SyncOutboxStore
import com.chen.memorizewords.data.sync.repository.sync.SyncOutboxWorkScheduler
import com.chen.memorizewords.domain.sync.PostLoginBootstrapResetter
import com.chen.memorizewords.domain.sync.SyncConflictPolicy
import com.chen.memorizewords.domain.sync.SyncLogoutFlusher
import com.chen.memorizewords.domain.sync.SyncOutboxReader
import com.chen.memorizewords.domain.sync.SyncOutboxWriter
import com.chen.memorizewords.domain.wordbook.repository.download.DownloadRepository
import com.chen.memorizewords.domain.sync.repository.SyncRepository
import com.chen.memorizewords.domain.sync.service.AuthenticatedRequestSuccessReporter
import com.chen.memorizewords.domain.account.repository.membership.MembershipRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds
    abstract fun bindSyncOutboxWriter(impl: DataSyncOutboxWriter): SyncOutboxWriter

    @Binds
    abstract fun bindSyncOutboxReader(impl: DataSyncOutboxReader): SyncOutboxReader

    @Binds
    abstract fun bindSyncConflictPolicy(impl: DataSyncConflictPolicy): SyncConflictPolicy

    @Binds
    abstract fun bindSyncLogoutFlusher(impl: DataSyncLogoutFlusher): SyncLogoutFlusher

    @Binds
    abstract fun bindPostLoginBootstrapResetter(
        impl: DataSyncPostLoginBootstrapResetter
    ): PostLoginBootstrapResetter

    @Binds
    abstract fun bindAuthenticatedRequestSuccessReporter(
        impl: SyncAuthenticatedRequestSuccessReporter
    ): AuthenticatedRequestSuccessReporter

    @Binds
    abstract fun bindSyncOutboxRetryWaitResumer(
        impl: SyncOutboxStore
    ): SyncOutboxRetryWaitResumer

    @Binds
    abstract fun bindSyncOutboxDrainScheduler(
        impl: SyncOutboxWorkScheduler
    ): SyncOutboxDrainScheduler

    @Binds
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds
    abstract fun bindMembershipRepository(impl: MembershipRepositoryImpl): MembershipRepository
}
