package com.chen.memorizewords.domain.account.policy

import com.chen.memorizewords.domain.account.model.LoginLocalDataAction
import javax.inject.Inject

class LoginLocalDataPolicy @Inject constructor() {
    fun resolve(
        isAuthenticated: Boolean,
        authenticatedUserId: Long?,
        retainedOwnerUserId: Long?,
        incomingUserId: Long,
        hasUnsyncedUserData: Boolean
    ): LoginLocalDataAction {
        return when {
            isAuthenticated &&
                authenticatedUserId != null &&
                authenticatedUserId != incomingUserId &&
                hasUnsyncedUserData -> LoginLocalDataAction.Block

            isAuthenticated &&
                authenticatedUserId != null &&
                authenticatedUserId != incomingUserId -> LoginLocalDataAction.Clear

            !isAuthenticated &&
                retainedOwnerUserId != null &&
                retainedOwnerUserId != incomingUserId -> LoginLocalDataAction.Clear

            else -> LoginLocalDataAction.Keep
        }
    }
}

