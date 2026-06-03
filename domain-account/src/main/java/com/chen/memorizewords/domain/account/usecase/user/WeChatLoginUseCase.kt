package com.chen.memorizewords.domain.account.usecase.user
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

class WeChatLoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val loginCompletionHandler: LoginCompletionHandler
) {
    suspend operator fun invoke(oauthCode: String, state: String? = null): Result<User> {
        return runCatching {
            if (oauthCode.isBlank()) throw LoginError.EmptyOauthCode()
            val loginResult = authRepository.loginByWechat(oauthCode.trim(), state).getOrThrow()
            loginCompletionHandler.complete(loginResult)
        }
    }
}
