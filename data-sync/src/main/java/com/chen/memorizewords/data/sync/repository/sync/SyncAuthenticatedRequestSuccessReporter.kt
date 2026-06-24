package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.domain.sync.service.AuthenticatedRequestSuccessReporter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncAuthenticatedRequestSuccessReporter @Inject constructor(
    private val syncOutboxRetryWaitResumer: SyncOutboxRetryWaitResumer,
    private val syncOutboxDrainScheduler: SyncOutboxDrainScheduler
) : AuthenticatedRequestSuccessReporter {

    override suspend fun onAuthenticatedRequestSucceeded() {
        syncOutboxRetryWaitResumer.resumeRetryWaiting()
        syncOutboxDrainScheduler.scheduleImmediateDrain()
    }
}
