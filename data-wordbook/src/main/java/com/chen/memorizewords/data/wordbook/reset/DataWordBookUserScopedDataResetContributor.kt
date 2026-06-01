package com.chen.memorizewords.data.wordbook.reset

import com.chen.memorizewords.data.wordbook.local.WordBookDatabase
import com.chen.memorizewords.data.wordbook.local.room.wordbook.WordBookSyncStateStore
import com.chen.memorizewords.domain.account.UserScopedDataResetContributor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DataWordBookUserScopedDataResetContributor @Inject constructor(
    private val database: WordBookDatabase,
    private val wordBookSyncStateStore: WordBookSyncStateStore
) : UserScopedDataResetContributor {
    override suspend fun clearUserScopedData() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            wordBookSyncStateStore.clearLocalState()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataWordBookUserScopedDataResetModule {
    @Binds
    @IntoSet
    abstract fun bindUserScopedDataResetContributor(
        impl: DataWordBookUserScopedDataResetContributor
    ): UserScopedDataResetContributor
}
