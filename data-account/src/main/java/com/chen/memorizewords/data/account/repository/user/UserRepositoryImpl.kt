package com.chen.memorizewords.data.account.repository.user

import com.chen.memorizewords.data.account.local.mmkv.auth.AuthLocalDataSource
import com.chen.memorizewords.data.account.mapper.toDomain
import com.chen.memorizewords.data.account.remote.user.AuthRemoteDataSource
import com.chen.memorizewords.data.account.remoteapi.api.auth.BindEmailRequest
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.UserRepository
import com.chen.memorizewords.data.account.remoteapi.api.auth.ProfilePatchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val remote: AuthRemoteDataSource,
    private val authLocal: AuthLocalDataSource
) : UserRepository {

    private suspend fun update(
        request: ProfilePatchRequest
    ): Result<User> = runCatching {
        withContext(Dispatchers.IO) {
            val onboardingCompleted = authLocal.getUser()?.onboardingCompleted ?: false
            val entity = remote.update(request).getOrThrow().toDomain(onboardingCompleted)
            authLocal.saveUser(entity)
            entity
        }
    }


    override suspend fun updateNickname(
        nickname: String
    ): Result<User> = update(ProfilePatchRequest(ProfilePatchRequest.Field.NICKNAME, nickname))

    override suspend fun updateGender(
        gender: String
    ): Result<User> = update(ProfilePatchRequest(ProfilePatchRequest.Field.GENDER, gender))

    override suspend fun updatePhone(
        phone: String
    ): Result<User> = update(ProfilePatchRequest(ProfilePatchRequest.Field.PHONE, phone))

    override suspend fun bindEmail(email: String, emailCode: String): Result<User> = runCatching {
        withContext(Dispatchers.IO) {
            val onboardingCompleted = authLocal.getUser()?.onboardingCompleted ?: false
            val entity = remote.bindEmail(
                BindEmailRequest(
                    email = email,
                    emailCode = emailCode
                )
            ).getOrThrow().toDomain(onboardingCompleted)
            authLocal.saveUser(entity)
            entity
        }
    }

    override suspend fun updateWechat(
        wechat: String
    ): Result<User> = update(ProfilePatchRequest(ProfilePatchRequest.Field.WECHAT, wechat))

    override suspend fun updateQQ(qq: String): Result<User> =
        update(ProfilePatchRequest(ProfilePatchRequest.Field.QQ, qq))

    override suspend fun uploadAvatar(imageBytes: ByteArray): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val body = imageBytes.toRequestBody("image/jpeg".toMediaType())
            val part = MultipartBody.Part.createFormData(
                "file",
                "avatar_${System.currentTimeMillis()}.jpg",
                body
            )
            val response = remote.uploadAvatar(part).getOrThrow()
            require(response.url.isNotBlank()) { "Avatar upload URL is empty" }
            response.url
        }
    }

    override suspend fun updateAvatar(avatarUrl: String): Result<User> =
        update(ProfilePatchRequest(ProfilePatchRequest.Field.AVATAR_URL, avatarUrl))
}
