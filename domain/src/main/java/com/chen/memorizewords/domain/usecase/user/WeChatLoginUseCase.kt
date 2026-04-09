package com.chen.memorizewords.domain.usecase.user

import com.chen.memorizewords.domain.model.user.User
import com.chen.memorizewords.domain.repository.user.AuthRepository
import javax.inject.Inject

class WeChatLoginUseCase @Inject constructor(
    private val repo: AuthRepository
) {
    suspend operator fun invoke(oauthCode: String, state: String? = null): Result<User> {
        if (oauthCode.isBlank()) return Result.failure(LoginError.EmptyOauthCode())
        return repo.loginByWechat(oauthCode.trim(), state)
    }
}
