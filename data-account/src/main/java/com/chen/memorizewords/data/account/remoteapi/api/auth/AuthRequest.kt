package com.chen.memorizewords.data.account.remoteapi.api.auth

import com.chen.memorizewords.core.network.http.NetworkRequestExecutor
import com.chen.memorizewords.core.network.http.AuthenticatedRequestOrigin
import com.chen.memorizewords.data.account.remoteapi.dto.LoginResponseDto
import com.chen.memorizewords.data.account.remoteapi.dto.ProfileDto
import com.chen.memorizewords.data.account.remoteapi.dto.SendSmsCodeResponseDto
import com.chen.memorizewords.core.network.http.ApiResponse
import com.chen.memorizewords.core.network.http.NetworkResult
import com.chen.memorizewords.core.network.http.await
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MultipartBody

@Singleton
class AuthRequest @Inject constructor(
    private val authApiService: AuthApiService,
    private val requestExecutor: NetworkRequestExecutor
) {

    suspend fun login(loginRequest: LoginRequest): NetworkResult<LoginResponseDto> =
        requestExecutor.executePublic {
            authApiService.login(loginRequest)
                .await<ApiResponse<LoginResponseDto>, LoginResponseDto>()
        }

    suspend fun register(
        request: RegisterRequest
    ): NetworkResult<LoginResponseDto> = requestExecutor.executePublic {
        authApiService.register(request)
            .await<ApiResponse<LoginResponseDto>, LoginResponseDto>()
    }

    suspend fun sendEmailCode(
        request: SendEmailCodeRequest
    ): NetworkResult<SendSmsCodeResponseDto> = requestExecutor.executePublic {
        authApiService.sendEmailCode(request)
            .await<ApiResponse<SendSmsCodeResponseDto>, SendSmsCodeResponseDto>()
    }

    suspend fun getProfile(): NetworkResult<ProfileDto> = requestExecutor.executeAuthenticated {
        authApiService.getProfile()
            .await<ApiResponse<ProfileDto>, ProfileDto>()
    }

    suspend fun refresh(refreshToken: String): NetworkResult<LoginResponseDto> =
        requestExecutor.executePublic {
            authApiService.refresh(RefreshRequest(refreshToken))
                .await<ApiResponse<LoginResponseDto>, LoginResponseDto>()
        }

    suspend fun getFusionAuthToken(): NetworkResult<FusionAuthTokenDto> =
        requestExecutor.executePublic {
            authApiService.getFusionAuthToken()
                .await<ApiResponse<FusionAuthTokenDto>, FusionAuthTokenDto>()
        }

    suspend fun logout(): NetworkResult<Unit> =
        requestExecutor.executeAuthenticated(AuthenticatedRequestOrigin.SYNC) {
        authApiService.logout()
            .await<ApiResponse<Unit>, Unit>()
    }

    suspend fun deleteAccount(): NetworkResult<Unit> =
        requestExecutor.executeAuthenticated(AuthenticatedRequestOrigin.SYNC) {
        authApiService.deleteAccount()
            .await<ApiResponse<Unit>, Unit>()
    }

    suspend fun changePassword(
        request: ChangePasswordRequest
    ): NetworkResult<Unit> = requestExecutor.executeAuthenticated {
        authApiService.changePassword(request)
            .await<ApiResponse<Unit>, Unit>()
    }


    suspend fun onboardingCompleted(): NetworkResult<Unit> = requestExecutor.executeAuthenticated {
        authApiService.onboardingCompleted()
            .await<ApiResponse<Unit>, Unit>()
    }

    suspend fun bindSocial(
        request: BindSocialRequest
    ): NetworkResult<ProfileDto> = requestExecutor.executeAuthenticated {
        authApiService.bindSocial(request)
            .await<ApiResponse<ProfileDto>, ProfileDto>()
    }

    suspend fun bindEmail(
        request: BindEmailRequest
    ): NetworkResult<ProfileDto> = requestExecutor.executeAuthenticated {
        authApiService.bindEmail(request)
            .await<ApiResponse<ProfileDto>, ProfileDto>()
    }

    suspend fun bindPhone(
        request: BindPhoneRequest
    ): NetworkResult<ProfileDto> = requestExecutor.executeAuthenticated {
        authApiService.bindPhone(request)
            .await<ApiResponse<ProfileDto>, ProfileDto>()
    }

    suspend fun uploadAvatar(file: MultipartBody.Part): NetworkResult<AvatarUploadDto> =
        requestExecutor.executeAuthenticated {
            authApiService.uploadAvatar(file)
                .await<ApiResponse<AvatarUploadDto>, AvatarUploadDto>()
        }

    suspend fun update(request: ProfilePatchRequest): NetworkResult<ProfileDto> =
        requestExecutor.executeAuthenticated {
            authApiService.update(request.toRequest())
                .await<ApiResponse<ProfileDto>, ProfileDto>()
        }
}
