package com.chen.memorizewords.domain.usecase.user

import com.chen.memorizewords.domain.model.user.User
import com.chen.memorizewords.domain.repository.user.AuthRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetUserFlowUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    operator fun invoke(): Flow<User?> = repository.getUserFlow()
}
