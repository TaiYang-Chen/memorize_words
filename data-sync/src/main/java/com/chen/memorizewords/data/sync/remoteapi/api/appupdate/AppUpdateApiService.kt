package com.chen.memorizewords.data.sync.remoteapi.api.appupdate

import com.chen.memorizewords.core.network.http.ApiResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AppUpdateApiService {
    @POST("app/update/check")
    fun checkUpdate(@Body request: AppUpdateCheckRequestDto): Call<ApiResponse<AppUpdateCheckResponseDto>>
}
