package com.chen.memorizewords.network.api.feedback

import com.chen.memorizewords.network.model.ApiResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface FeedbackApiService {
    @Multipart
    @POST("me/feedback")
    fun submitFeedback(
        @Part("content") content: RequestBody,
        @Part("contact") contact: RequestBody?,
        @Part images: List<MultipartBody.Part>
    ): Call<ApiResponse<Unit>>
}
