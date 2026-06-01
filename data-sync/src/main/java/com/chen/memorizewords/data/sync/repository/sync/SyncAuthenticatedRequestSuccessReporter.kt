package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.domain.sync.service.AuthenticatedRequestSuccessReporter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncAuthenticatedRequestSuccessReporter @Inject constructor(
    private val syncOutboxWorkScheduler: SyncOutboxWorkScheduler
) : AuthenticatedRequestSuccessReporter {

    override fun onAuthenticatedRequestSucceeded() {
        syncOutboxWorkScheduler.scheduleDrain()
    }
}
