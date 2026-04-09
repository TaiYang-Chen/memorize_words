package com.chen.memorizewords.data.remote.user

import com.chen.memorizewords.data.remote.RemoteResultAdapter
import com.chen.memorizewords.network.api.auth.LoginRequest
import com.chen.memorizewords.network.api.auth.AuthRequest
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
import javax.inject.Inject

class RemoteAuthDataSourceImpl @Inject constructor(
    private val authRequest: AuthRequest,
    private val remoteResultAdapter: RemoteResultAdapter
) : AuthRemoteDataSource {

    override suspend fun login(loginRequest: LoginRequest): Result<LoginResponseDto> {
        return remoteResultAdapter.toResult { authRequest.login(loginRequest) }
    }

    override suspend fun loginBySms(loginRequest: LoginRequest): Result<LoginResponseDto> {
        return remoteResultAdapter.toResult { authRequest.login(loginRequest) }
    }

    override suspend fun loginByWechat(loginRequest: LoginRequest): Result<LoginResponseDto> {
        return remoteResultAdapter.toResult { authRequest.login(loginRequest) }
    }

    override suspend fun loginByQq(loginRequest: LoginRequest): Result<LoginResponseDto> {
        return remoteResultAdapter.toResult { authRequest.login(loginRequest) }
    }

    override suspend fun sendLoginSmsCode(request: SendSmsCodeRequest): Result<SendSmsCodeResponseDto> {
        return remoteResultAdapter.toResult { authRequest.sendLoginSmsCode(request) }
    }

    override suspend fun register(request: RegisterRequest): Result<LoginResponseDto> {
        return remoteResultAdapter.toResult { authRequest.register(request) }
    }

    override suspend fun getProfile(): Result<ProfileDto> {
        return remoteResultAdapter.toResult { authRequest.getProfile() }
    }

    override suspend fun logout(): Result<Unit> {
        return remoteResultAdapter.toResult { authRequest.logout() }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return remoteResultAdapter.toResult { authRequest.deleteAccount() }
    }

    override suspend fun changePassword(request: ChangePasswordRequest): Result<Unit> {
        return remoteResultAdapter.toResult { authRequest.changePassword(request) }
    }

    override suspend fun bindSocial(request: BindSocialRequest): Result<ProfileDto> {
        return remoteResultAdapter.toResult { authRequest.bindSocial(request) }
    }

    override suspend fun uploadAvatar(file: MultipartBody.Part): Result<AvatarUploadDto> {
        return remoteResultAdapter.toResult { authRequest.uploadAvatar(file) }
    }

    override suspend fun update(request: ProfilePatchRequest): Result<ProfileDto> {
        return remoteResultAdapter.toResult { authRequest.update(request) }
    }
}
