package com.chen.memorizewords.data.repository.sync

import com.chen.memorizewords.domain.service.sync.AuthenticatedRequestSuccessReporter
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
