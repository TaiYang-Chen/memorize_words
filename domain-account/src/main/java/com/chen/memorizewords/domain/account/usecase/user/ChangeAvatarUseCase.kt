package com.chen.memorizewords.domain.account.usecase.user
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.UserRepository
import javax.inject.Inject

class ChangeAvatarUseCase @Inject constructor(
    private val repo: UserRepository
) {
    suspend operator fun invoke(imageBytes: ByteArray): Result<User> {
        if (imageBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Avatar image is empty"))
        }
        return repo.uploadAvatar(imageBytes).fold(
            onSuccess = { url -> repo.updateAvatar(url) },
            onFailure = { throwable -> Result.failure(throwable) }
        )
    }
}
