package com.chen.memorizewords.domain.sync.usecase
import com.chen.memorizewords.domain.sync.repository.SyncRepository
import javax.inject.Inject

class TriggerSyncDrainUseCase @Inject constructor(
    private val repository: SyncRepository
) {
    operator fun invoke() {
        repository.triggerDrain()
    }
}
