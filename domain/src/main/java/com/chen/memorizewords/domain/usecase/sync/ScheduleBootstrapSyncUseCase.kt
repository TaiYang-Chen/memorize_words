package com.chen.memorizewords.domain.usecase.sync

import com.chen.memorizewords.domain.repository.sync.SyncRepository
import javax.inject.Inject

class ScheduleBootstrapSyncUseCase @Inject constructor(
    private val repository: SyncRepository
) {
    operator fun invoke() {
        repository.scheduleBootstrapSync()
    }
}
