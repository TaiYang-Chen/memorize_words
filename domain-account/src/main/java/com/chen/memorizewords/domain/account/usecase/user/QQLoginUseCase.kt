package com.chen.memorizewords.domain.account.usecase.user
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

class QQLoginUseCase @Inject constructor(
    private val repo: AuthRepository
) {
    suspend operator fun invoke(oauthCode: String, state: String? = null): Result<User> {
        if (oauthCode.isBlank()) return Result.failure(LoginError.EmptyOauthCode())
        return repo.loginByQq(oauthCode.trim(), state)
    }
}
