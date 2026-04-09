package com.chen.memorizewords.domain.usecase.sync

import com.chen.memorizewords.domain.repository.sync.SyncRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPendingSyncCountUseCase @Inject constructor(
    private val repository: SyncRepository
) {
    operator fun invoke(): Flow<Int> {
        return repository.observePendingSyncCount()
    }
}
