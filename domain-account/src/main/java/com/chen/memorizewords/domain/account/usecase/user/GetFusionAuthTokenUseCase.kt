package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.FusionAuthToken
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

class GetFusionAuthTokenUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<FusionAuthToken> {
        return authRepository.getFusionAuthToken()
    }
}
