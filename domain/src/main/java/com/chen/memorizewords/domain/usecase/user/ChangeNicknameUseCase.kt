package com.chen.memorizewords.domain.usecase.user

import com.chen.memorizewords.domain.model.user.User
import com.chen.memorizewords.domain.repository.user.UserRepository
import javax.inject.Inject

class ChangeNicknameUseCase @Inject constructor(private val repo: UserRepository) {
    suspend operator fun invoke(nickname: String): Result<User> = repo.updateNickname(nickname)
}