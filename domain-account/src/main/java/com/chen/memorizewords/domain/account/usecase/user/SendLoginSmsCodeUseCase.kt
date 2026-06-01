package com.chen.memorizewords.domain.account.usecase.user
import com.chen.memorizewords.domain.account.model.user.SmsCodeMeta
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

class SendLoginSmsCodeUseCase @Inject constructor(
    private val repo: AuthRepository
) {
    suspend operator fun invoke(phone: String): Result<SmsCodeMeta> {
        if (phone.isBlank()) return Result.failure(LoginError.EmptyPhone())
        return repo.sendLoginSmsCode(phone.trim())
    }
}
