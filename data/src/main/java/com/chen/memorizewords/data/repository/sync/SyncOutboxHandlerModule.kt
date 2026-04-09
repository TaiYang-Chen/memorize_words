package com.chen.memorizewords.data.repository.sync

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncOutboxHandlerModule {

    @Binds
    @IntoSet
    abstract fun bindUserSyncOutboxHandler(impl: UserSyncOutboxHandler): SyncOutboxHandler

    @Binds
    @IntoSet
    abstract fun bindLearningSyncOutboxHandler(impl: LearningSyncOutboxHandler): SyncOutboxHandler
}
