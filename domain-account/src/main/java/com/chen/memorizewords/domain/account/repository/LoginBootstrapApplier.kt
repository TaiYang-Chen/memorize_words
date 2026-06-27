package com.chen.memorizewords.domain.account.repository

import com.chen.memorizewords.domain.sync.model.LoginBootstrap

interface LoginBootstrapApplier {
    suspend fun apply(bootstrap: LoginBootstrap?)
}
