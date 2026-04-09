package com.chen.memorizewords.network.util

import com.chen.memorizewords.network.model.ApiResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>

    sealed class Failure : NetworkResult<Nothing> {
        data class HttpError(val code: Int, val message: String? = null) : Failure()
        data class Unauthorized(
            val code: Int = 401,
            val message: String? = null,
            val path: String? = null
        ) : Failure()

        data class NetworkError(val throwable: Throwable) : Failure()
        data class GenericError(val message: String) : Failure()
    }
}

@Throws(Exception::class)
suspend inline fun <reified T, reified R> Call<T>.await(): NetworkResult<R> =
    awaitInternal(bodyPolicy = BodyPolicy.RequireBody)

@Throws(Exception::class)
suspend inline fun <reified T, reified R> Call<T>.awaitNullable(): NetworkResult<R?> =
    awaitInternal(bodyPolicy = BodyPolicy.AllowNullBody)

@Throws(Exception::class)
suspend inline fun <reified T, reified R> Call<T>.awaitInternal(
    bodyPolicy: BodyPolicy
): NetworkResult<R> = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback<T> {
        override fun onResponse(call: Call<T>, response: Response<T>) {
            continuation.resumeWith(
                runCatching {
                    if (!response.isSuccessful) {
                        return@runCatching ApiResponseParser.httpFailure(
                            code = response.code(),
                            message = response.message()
                        )
                    }

                    val encodedPath = call.request().url.encodedPath
                    when (bodyPolicy) {
                        BodyPolicy.RequireBody ->
                            ApiResponseParser.parseRequired(
                                apiResponse = response.body() as? ApiResponse<R>,
                                encodedPath = encodedPath,
                                successTypeIsUnit = R::class == Unit::class
                            )

                        BodyPolicy.AllowNullBody ->
                            ApiResponseParser.parseNullable(
                                apiResponse = response.body() as? ApiResponse<R?>,
                                encodedPath = encodedPath
                            ) as NetworkResult<R>
                    }
                }
            )
        }

        override fun onFailure(call: Call<T>, t: Throwable) {
            continuation.resumeWith(
                runCatching {
                    NetworkResult.Failure.NetworkError(t)
                }
            )
        }
    })

    continuation.invokeOnCancellation {
        if (continuation.isCancelled) {
            cancel()
        }
    }
}
