package com.chen.memorizewords.data.remote.user

import com.chen.memorizewords.network.api.auth.LoginRequest
import com.chen.memorizewords.network.api.auth.RegisterRequest
import com.chen.memorizewords.network.api.auth.SendSmsCodeRequest
import com.chen.memorizewords.network.api.auth.ChangePasswordRequest
import com.chen.memorizewords.network.api.auth.BindSocialRequest
import com.chen.memorizewords.network.api.auth.AvatarUploadDto
import com.chen.memorizewords.network.api.auth.ProfilePatchRequest
import com.chen.memorizewords.network.dto.LoginResponseDto
import com.chen.memorizewords.network.dto.ProfileDto
import com.chen.memorizewords.network.dto.SendSmsCodeResponseDto
import okhttp3.MultipartBody

/**
 * AuthRemoteDataSource
 *
 * 远端认证数据源的抽象接口，定义了与后端交互的最小契约。
 * 所有方法都返回 kotlin.Result 用于统一的成功/失败语义封装。
 */
interface AuthRemoteDataSource {

    suspend fun login(loginRequest: LoginRequest): Result<LoginResponseDto>

    suspend fun loginBySms(loginRequest: LoginRequest): Result<LoginResponseDto>

    suspend fun loginByWechat(loginRequest: LoginRequest): Result<LoginResponseDto>

    suspend fun loginByQq(loginRequest: LoginRequest): Result<LoginResponseDto>

    suspend fun sendLoginSmsCode(request: SendSmsCodeRequest): Result<SendSmsCodeResponseDto>

    suspend fun register(request: RegisterRequest): Result<LoginResponseDto>

    suspend fun getProfile(): Result<ProfileDto>

    suspend fun logout(): Result<Unit>

    suspend fun deleteAccount(): Result<Unit>

    suspend fun changePassword(request: ChangePasswordRequest): Result<Unit>

    suspend fun bindSocial(request: BindSocialRequest): Result<ProfileDto>

    suspend fun uploadAvatar(file: MultipartBody.Part): Result<AvatarUploadDto>

    suspend fun update(request: ProfilePatchRequest): Result<ProfileDto>
}
