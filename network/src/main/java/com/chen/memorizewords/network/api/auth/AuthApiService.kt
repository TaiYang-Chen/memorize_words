package com.chen.memorizewords.network.api.auth

import com.chen.memorizewords.network.dto.LoginResponseDto
import com.chen.memorizewords.network.dto.ProfileDto
import com.chen.memorizewords.network.dto.SendSmsCodeResponseDto
import com.chen.memorizewords.network.model.ApiResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.Part
import retrofit2.http.POST

interface AuthApiService {

    @POST("auth/login")
    fun login(@Body request: LoginRequest): Call<ApiResponse<LoginResponseDto>>

    @POST("auth/register")
    fun register(@Body request: RegisterRequest): Call<ApiResponse<LoginResponseDto>>

    @POST("auth/sms/send-code")
    fun sendSmsCode(@Body request: SendSmsCodeRequest): Call<ApiResponse<SendSmsCodeResponseDto>>

    @POST("auth/refresh")
    fun refresh(@Body request: RefreshRequest): Call<ApiResponse<LoginResponseDto>>

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

    @Multipart
    @POST("upload/avatar")
    fun uploadAvatar(@Part file: okhttp3.MultipartBody.Part): Call<ApiResponse<AvatarUploadDto>>

    @PATCH("me/profile")
    fun update(
        @Body fields: Map<String, String>
    ): Call<ApiResponse<ProfileDto>>
}
