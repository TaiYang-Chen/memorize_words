package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.domain.sync.SyncOutboxHandler
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
    abstract fun bindDataSyncLearningOutboxHandler(
        impl: DataSyncLearningOutboxHandler
    ): SyncOutboxHandler
}
