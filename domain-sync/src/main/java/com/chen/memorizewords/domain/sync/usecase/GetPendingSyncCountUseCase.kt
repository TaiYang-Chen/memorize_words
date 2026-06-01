package com.chen.memorizewords.domain.sync.usecase
import com.chen.memorizewords.domain.sync.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPendingSyncCountUseCase @Inject constructor(
    private val repository: SyncRepository
) {
    operator fun invoke(): Flow<Int> {
        return repository.observePendingSyncCount()
    }
}
