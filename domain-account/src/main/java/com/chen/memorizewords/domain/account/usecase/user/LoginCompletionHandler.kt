package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.AuthLoginResult
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.AccountSessionRepository
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import javax.inject.Inject

class LoginCompletionHandler @Inject constructor(
    private val localAccountRepository: LocalAccountRepository,
    private val accountSessionRepository: AccountSessionRepository,
) {
    suspend fun complete(loginResult: AuthLoginResult): User {
        val user = loginResult.user
        accountSessionRepository.saveSession(loginResult.session)
        localAccountRepository.saveUser(user)
        return user
    }
}
