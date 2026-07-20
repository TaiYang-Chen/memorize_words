package com.chen.memorizewords.data.floating.reset

import com.chen.memorizewords.data.floating.local.FloatingDatabase
import com.chen.memorizewords.domain.account.UserScopedDataResetContributor
import com.chen.memorizewords.domain.floating.FloatingSettingsLocalStatePort
import com.chen.memorizewords.domain.floating.repository.FloatingActivationStateRepository
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadStatus
import com.chen.memorizewords.domain.floating.repository.CharacterPackRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class DataFloatingUserScopedDataResetContributor @Inject constructor(
    private val database: FloatingDatabase,
    private val floatingSettingsLocalStatePort: FloatingSettingsLocalStatePort,
    private val floatingActivationStateRepository: FloatingActivationStateRepository,
    private val characterPackRepository: CharacterPackRepository
) : UserScopedDataResetContributor {
    override suspend fun clearUserScopedData() {
        withContext(Dispatchers.IO) {
            characterPackRepository.observeDownloads().first().values
                .filter { download ->
                    download.downloadRequestId != null &&
                        (
                            download.selectAfterInstall ||
                                download.status == CharacterPackDownloadStatus.QUEUED ||
                                download.status == CharacterPackDownloadStatus.DOWNLOADING ||
                                download.status == CharacterPackDownloadStatus.INSTALLING
                            )
                }
                .forEach { download ->
                    runCatching { characterPackRepository.cancelDownload(download.packId) }
                }
            database.clearAllTables()
            floatingSettingsLocalStatePort.clearLocalState()
            floatingActivationStateRepository.clearPending()
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
