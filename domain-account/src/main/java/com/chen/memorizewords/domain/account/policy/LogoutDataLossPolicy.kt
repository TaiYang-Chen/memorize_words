package com.chen.memorizewords.domain.account.policy

import javax.inject.Inject

class LogoutDataLossPolicy @Inject constructor() {
    fun shouldAbortAfterFlush(force: Boolean, pendingSyncCount: Int): Boolean {
        return !force && pendingSyncCount > 0
    }
}

