package com.chen.memorizewords.domain.account.usecase.user
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.UserRepository
import javax.inject.Inject

class ChangeGenderUseCase @Inject constructor(private val repo: UserRepository) {
    suspend operator fun invoke(gender: String): Result<User> = repo.updateGender(gender)
}