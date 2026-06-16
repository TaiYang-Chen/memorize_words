package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.UserRepository
import javax.inject.Inject

class CacheLoadedAvatarUseCase @Inject constructor(
    private val repo: UserRepository
) {
    suspend operator fun invoke(imageBytes: ByteArray, avatarUrl: String?): Result<User> {
        if (imageBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Avatar image is empty"))
        }
        return repo.cacheLoadedAvatar(imageBytes, avatarUrl)
    }
}
