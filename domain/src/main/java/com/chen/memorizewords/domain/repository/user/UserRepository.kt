package com.chen.memorizewords.domain.repository.user

import com.chen.memorizewords.domain.model.user.User

interface UserRepository {

    suspend fun updateNickname(nickname: String): Result<User>
    suspend fun updateGender(gender: String): Result<User>

    suspend fun updatePhone(phone: String): Result<User>

    suspend fun updateWechat(wechat: String): Result<User>

    suspend fun updateQQ(qq: String): Result<User>

    suspend fun uploadAvatar(imageBytes: ByteArray): Result<String>

    suspend fun updateAvatar(avatarUrl: String): Result<User>
}
