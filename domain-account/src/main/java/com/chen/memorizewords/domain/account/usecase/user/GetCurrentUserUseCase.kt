package com.chen.memorizewords.domain.account.usecase.user
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import javax.inject.Inject

class GetCurrentUserUseCase @Inject constructor(
    private val repository: LocalAccountRepository
) {
    suspend operator fun invoke(): User? = repository.getCurrentUser()
}
