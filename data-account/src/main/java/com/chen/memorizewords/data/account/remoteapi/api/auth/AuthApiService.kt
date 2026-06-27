package com.chen.memorizewords.data.account.remoteapi.api.auth

import com.chen.memorizewords.data.account.remoteapi.dto.LoginResponseDto
import com.chen.memorizewords.data.account.remoteapi.dto.ProfileDto
import com.chen.memorizewords.data.account.remoteapi.dto.SendSmsCodeResponseDto
import com.chen.memorizewords.core.network.http.ApiResponse
import com.chen.memorizewords.core.network.CoreNetworkHeaders
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.Part
import retrofit2.http.POST

interface AuthApiService {

    @Headers("${CoreNetworkHeaders.SKIP_AUTHORIZATION}: true")
    @POST("auth/login")
    fun login(@Body request: LoginRequest): Call<ApiResponse<LoginResponseDto>>

    @Headers("${CoreNetworkHeaders.SKIP_AUTHORIZATION}: true")
    @POST("auth/register")
    fun register(@Body request: RegisterRequest): Call<ApiResponse<LoginResponseDto>>

    @Headers("${CoreNetworkHeaders.SKIP_AUTHORIZATION}: true")
    @POST("auth/email/send-code")
    fun sendEmailCode(@Body request: SendEmailCodeRequest): Call<ApiResponse<SendSmsCodeResponseDto>>

    @Headers("${CoreNetworkHeaders.SKIP_AUTHORIZATION}: true")
    @POST("auth/refresh")
    fun refresh(@Body request: RefreshRequest): Call<ApiResponse<LoginResponseDto>>

    @Headers("${CoreNetworkHeaders.SKIP_AUTHORIZATION}: true")
    @GET("auth/fusion/token")
    fun getFusionAuthToken(): Call<ApiResponse<FusionAuthTokenDto>>

    @Headers("${CoreNetworkHeaders.SKIP_AUTHORIZATION}: true")
    @POST("auth/fusion/login")
    fun fusionLogin(@Body request: FusionLoginRequest): Call<ApiResponse<LoginResponseDto>>

    @Headers("${CoreNetworkHeaders.SKIP_AUTHORIZATION}: true")
    @POST("auth/fusion/register")
    fun fusionRegister(@Body request: FusionRegisterRequest): Call<ApiResponse<LoginResponseDto>>

    @GET("me")
    fun getProfile(): Call<ApiResponse<ProfileDto>>

    @POST("auth/logout")
    fun logout(): Call<ApiResponse<Unit>>

    @POST("me/account/delete")
    fun deleteAccount(): Call<ApiResponse<Unit>>

    @POST("auth/change-password")
    fun changePassword(@Body request: ChangePasswordRequest): Call<ApiResponse<Unit>>

    @POST("auth/bind-social")
    fun bindSocial(@Body request: BindSocialRequest): Call<ApiResponse<ProfileDto>>

    @POST("auth/bind-email")
    fun bindEmail(@Body request: BindEmailRequest): Call<ApiResponse<ProfileDto>>

    @POST("auth/bind-phone")
    fun bindPhone(@Body request: BindPhoneRequest): Call<ApiResponse<ProfileDto>>

    @Multipart
    @POST("upload/avatar")
    fun uploadAvatar(@Part file: okhttp3.MultipartBody.Part): Call<ApiResponse<AvatarUploadDto>>

    @PATCH("me/profile")
    fun update(
        @Body fields: Map<String, String>
    ): Call<ApiResponse<ProfileDto>>

    @GET("auth/onboardingCompleted")
    fun onboardingCompleted(): Call<ApiResponse<Unit>>
}
