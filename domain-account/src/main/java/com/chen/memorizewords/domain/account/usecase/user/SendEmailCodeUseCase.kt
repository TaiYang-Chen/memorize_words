package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.user.SmsCodeMeta
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

class SendEmailCodeUseCase @Inject constructor(
    private val repo: AuthRepository
) {
    suspend operator fun invoke(email: String, scene: String = "login"): Result<SmsCodeMeta> {
        if (email.isBlank()) return Result.failure(LoginError.EmptyEmail())
        return repo.sendEmailCode(email.trim(), scene)
    }
}
