package com.chen.memorizewords.data.account.remote.user

import com.chen.memorizewords.data.account.remoteapi.api.auth.LoginRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.RegisterRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.SendSmsCodeRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.SendEmailCodeRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.ChangePasswordRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.BindSocialRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.BindEmailRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.AvatarUploadDto
import com.chen.memorizewords.data.account.remoteapi.api.auth.ProfilePatchRequest
import com.chen.memorizewords.data.account.remoteapi.dto.LoginResponseDto
import com.chen.memorizewords.data.account.remoteapi.dto.ProfileDto
import com.chen.memorizewords.data.account.remoteapi.dto.SendSmsCodeResponseDto
import okhttp3.MultipartBody

/**
 * AuthRemoteDataSource
 *
 * 远端认证数据源的抽象接口，定义了与后端交互的最小契约。
 * 所有方法都返回 kotlin.Result，用于统一封装成功/失败语义。
 */
interface AuthRemoteDataSource {

    suspend fun login(loginRequest: LoginRequest): Result<LoginResponseDto>

    suspend fun loginBySms(loginRequest: LoginRequest): Result<LoginResponseDto>

    suspend fun loginByWechat(loginRequest: LoginRequest): Result<LoginResponseDto>

    suspend fun loginByQq(loginRequest: LoginRequest): Result<LoginResponseDto>

    suspend fun sendLoginSmsCode(request: SendSmsCodeRequest): Result<SendSmsCodeResponseDto>

    suspend fun sendEmailCode(request: SendEmailCodeRequest): Result<SendSmsCodeResponseDto>

    suspend fun register(request: RegisterRequest): Result<LoginResponseDto>

    suspend fun getProfile(): Result<ProfileDto>

    suspend fun logout(): Result<Unit>

    suspend fun deleteAccount(): Result<Unit>

    suspend fun changePassword(request: ChangePasswordRequest): Result<Unit>

    suspend fun onboardingCompleted(): Result<Unit>

    suspend fun bindSocial(request: BindSocialRequest): Result<ProfileDto>

    suspend fun bindEmail(request: BindEmailRequest): Result<ProfileDto>

    suspend fun uploadAvatar(file: MultipartBody.Part): Result<AvatarUploadDto>

    suspend fun update(request: ProfilePatchRequest): Result<ProfileDto>
}
