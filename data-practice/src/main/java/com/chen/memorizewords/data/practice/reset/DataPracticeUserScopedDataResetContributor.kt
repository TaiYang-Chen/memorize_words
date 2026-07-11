package com.chen.memorizewords.data.practice.reset

import com.chen.memorizewords.data.practice.local.PracticeDatabase
import com.chen.memorizewords.domain.account.UserScopedDataResetContributor
import com.chen.memorizewords.domain.practice.PracticeSettingsLocalStatePort
import com.chen.memorizewords.domain.practice.usage.PracticeUsageRepository
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
class DataPracticeUserScopedDataResetContributor @Inject constructor(
    private val database: PracticeDatabase,
    private val practiceSettingsLocalStatePort: PracticeSettingsLocalStatePort,
    private val practiceUsageRepository: PracticeUsageRepository
) : UserScopedDataResetContributor {
    override suspend fun clearUserScopedData() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            practiceSettingsLocalStatePort.clearLocalState()
            practiceUsageRepository.clear()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataPracticeUserScopedDataResetModule {
    @Binds
    @IntoSet
    abstract fun bindUserScopedDataResetContributor(
        impl: DataPracticeUserScopedDataResetContributor
    ): UserScopedDataResetContributor
}
