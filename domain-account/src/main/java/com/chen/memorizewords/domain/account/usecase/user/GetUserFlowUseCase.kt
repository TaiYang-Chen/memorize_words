package com.chen.memorizewords.domain.account.usecase.user
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetUserFlowUseCase @Inject constructor(
    private val repository: LocalAccountRepository
) {
    operator fun invoke(): Flow<User?> = repository.getUserFlow()
}
