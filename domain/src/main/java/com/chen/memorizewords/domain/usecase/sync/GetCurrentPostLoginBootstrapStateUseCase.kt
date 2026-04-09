package com.chen.memorizewords.domain.usecase.sync

import com.chen.memorizewords.domain.model.sync.PostLoginBootstrapState
import com.chen.memorizewords.domain.repository.sync.SyncRepository
import javax.inject.Inject

class GetCurrentPostLoginBootstrapStateUseCase @Inject constructor(
    private val repository: SyncRepository
) {
    operator fun invoke(): PostLoginBootstrapState {
        return repository.getCurrentPostLoginBootstrapState()
    }
}
