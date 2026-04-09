package com.chen.memorizewords.domain.usecase.user

import com.chen.memorizewords.domain.model.user.SmsCodeMeta
import com.chen.memorizewords.domain.repository.user.AuthRepository
import javax.inject.Inject

class SendLoginSmsCodeUseCase @Inject constructor(
    private val repo: AuthRepository
) {
    suspend operator fun invoke(phone: String): Result<SmsCodeMeta> {
        if (phone.isBlank()) return Result.failure(LoginError.EmptyPhone())
        return repo.sendLoginSmsCode(phone.trim())
    }
}
