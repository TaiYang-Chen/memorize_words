package com.chen.memorizewords.data.floating.reset

import com.chen.memorizewords.data.floating.local.FloatingDatabase
import com.chen.memorizewords.domain.account.UserScopedDataResetContributor
import com.chen.memorizewords.domain.floating.FloatingSettingsLocalStatePort
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
class DataFloatingUserScopedDataResetContributor @Inject constructor(
    private val database: FloatingDatabase,
    private val floatingSettingsLocalStatePort: FloatingSettingsLocalStatePort
) : UserScopedDataResetContributor {
    override suspend fun clearUserScopedData() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            floatingSettingsLocalStatePort.clearLocalState()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataFloatingUserScopedDataResetModule {
    @Binds
    @IntoSet
    abstract fun bindUserScopedDataResetContributor(
        impl: DataFloatingUserScopedDataResetContributor
    ): UserScopedDataResetContributor
}
