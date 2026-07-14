package com.chen.memorizewords.data.sync.di

import com.chen.memorizewords.data.sync.repository.download.DownloadRepositoryImpl
import com.chen.memorizewords.data.sync.remote.appupdate.AppUpdateRepositoryImpl
import com.chen.memorizewords.data.sync.repository.sync.DataSyncConflictPolicy
import com.chen.memorizewords.data.sync.repository.sync.DataSyncLogoutFlusher
import com.chen.memorizewords.data.sync.repository.sync.DataSyncPostLoginBootstrapResetter
import com.chen.memorizewords.data.sync.repository.membership.MembershipRepositoryImpl
import com.chen.memorizewords.data.sync.repository.sync.SyncRepositoryImpl
import com.chen.memorizewords.data.sync.bootstrap.HomeStartupSnapshotStore
import com.chen.memorizewords.data.sync.bootstrap.LoginBootstrapApplierImpl
import com.chen.memorizewords.domain.account.repository.LoginBootstrapApplier
import com.chen.memorizewords.domain.sync.PostLoginBootstrapResetter
import com.chen.memorizewords.domain.sync.SyncConflictPolicy
import com.chen.memorizewords.domain.sync.SyncLogoutFlusher
import com.chen.memorizewords.domain.wordbook.repository.download.DownloadRepository
import com.chen.memorizewords.domain.sync.repository.HomeStartupSnapshotRepository
import com.chen.memorizewords.domain.sync.repository.SyncRepository
import com.chen.memorizewords.domain.account.repository.membership.MembershipRepository
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateRepository
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
    abstract fun bindSyncConflictPolicy(impl: DataSyncConflictPolicy): SyncConflictPolicy

    @Binds
    abstract fun bindSyncLogoutFlusher(impl: DataSyncLogoutFlusher): SyncLogoutFlusher

    @Binds
    abstract fun bindPostLoginBootstrapResetter(
        impl: DataSyncPostLoginBootstrapResetter
    ): PostLoginBootstrapResetter

    @Binds
    abstract fun bindLoginBootstrapApplier(
        impl: LoginBootstrapApplierImpl
    ): LoginBootstrapApplier

    @Binds
    abstract fun bindHomeStartupSnapshotRepository(
        impl: HomeStartupSnapshotStore
    ): HomeStartupSnapshotRepository

    @Binds
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds
    abstract fun bindMembershipRepository(impl: MembershipRepositoryImpl): MembershipRepository

    @Binds
    abstract fun bindAppUpdateRepository(impl: AppUpdateRepositoryImpl): AppUpdateRepository
}
