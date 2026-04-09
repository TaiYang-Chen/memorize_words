package com.chen.memorizewords.domain.usecase.user

import com.chen.memorizewords.domain.repository.user.AuthRepository
import javax.inject.Inject

class IsLoggedInUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    operator fun invoke(): Boolean = repository.isLoggedIn()
}
