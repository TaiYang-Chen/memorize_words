package com.chen.memorizewords.data.account.remote.user

import com.chen.memorizewords.core.network.remote.RemoteResultAdapter
import com.chen.memorizewords.data.account.remoteapi.api.auth.LoginRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.AuthRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.RegisterRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.SendEmailCodeRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.FusionAuthTokenDto
import com.chen.memorizewords.data.account.remoteapi.api.auth.FusionRegisterRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.ChangePasswordRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.BindSocialRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.BindEmailRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.BindPhoneRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.AvatarUploadDto
import com.chen.memorizewords.data.account.remoteapi.api.auth.ProfilePatchRequest
import com.chen.memorizewords.data.account.remoteapi.dto.LoginResponseDto
import com.chen.memorizewords.data.account.remoteapi.dto.ProfileDto
import com.chen.memorizewords.data.account.remoteapi.dto.SendSmsCodeResponseDto
import okhttp3.MultipartBody
import javax.inject.Inject

class RemoteAuthDataSourceImpl @Inject constructor(
    private val authRequest: AuthRequest,
    private val remoteResultAdapter: RemoteResultAdapter
) : AuthRemoteDataSource {

    override suspend fun login(loginRequest: LoginRequest): Result<LoginResponseDto> {
        return remoteResultAdapter.toResult { authRequest.login(loginRequest) }
    }

    override suspend fun loginByWechat(loginRequest: LoginRequest): Result<LoginResponseDto> {
        return remoteResultAdapter.toResult { authRequest.login(loginRequest) }
    }

    override suspend fun loginByQq(loginRequest: LoginRequest): Result<LoginResponseDto> {
        return remoteResultAdapter.toResult { authRequest.login(loginRequest) }
    }

    override suspend fun sendEmailCode(request: SendEmailCodeRequest): Result<SendSmsCodeResponseDto> {
        return remoteResultAdapter.toResult { authRequest.sendEmailCode(request) }
    }

    override suspend fun register(request: RegisterRequest): Result<LoginResponseDto> {
        return remoteResultAdapter.toResult { authRequest.register(request) }
    }

    override suspend fun getFusionAuthToken(): Result<FusionAuthTokenDto> {
        return remoteResultAdapter.toResult { authRequest.getFusionAuthToken() }
    }

    override suspend fun fusionRegister(request: FusionRegisterRequest): Result<LoginResponseDto> {
        return remoteResultAdapter.toResult { authRequest.fusionRegister(request) }
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

    override suspend fun onboardingCompleted(): Result<Unit> {
        return remoteResultAdapter.toResult { authRequest.onboardingCompleted() }
    }

    override suspend fun bindSocial(request: BindSocialRequest): Result<ProfileDto> {
        return remoteResultAdapter.toResult { authRequest.bindSocial(request) }
    }

    override suspend fun bindEmail(request: BindEmailRequest): Result<ProfileDto> {
        return remoteResultAdapter.toResult { authRequest.bindEmail(request) }
    }

    override suspend fun bindPhone(request: BindPhoneRequest): Result<ProfileDto> {
        return remoteResultAdapter.toResult { authRequest.bindPhone(request) }
    }

    override suspend fun uploadAvatar(file: MultipartBody.Part): Result<AvatarUploadDto> {
        return remoteResultAdapter.toResult { authRequest.uploadAvatar(file) }
    }

    override suspend fun update(request: ProfilePatchRequest): Result<ProfileDto> {
        return remoteResultAdapter.toResult { authRequest.update(request) }
    }
}
