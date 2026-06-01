package com.chen.memorizewords.domain.sync.usecase
import com.chen.memorizewords.domain.sync.model.PostLoginBootstrapState
import com.chen.memorizewords.domain.sync.repository.SyncRepository
import javax.inject.Inject

class GetCurrentPostLoginBootstrapStateUseCase @Inject constructor(
    private val repository: SyncRepository
) {
    operator fun invoke(): PostLoginBootstrapState {
        return repository.getCurrentPostLoginBootstrapState()
    }
}
