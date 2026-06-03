package com.chen.memorizewords.domain.account.usecase.user
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import javax.inject.Inject

class IsLoggedInUseCase @Inject constructor(
    private val repository: LocalAccountRepository
) {
    operator fun invoke(): Boolean = repository.isLoggedIn()
}
