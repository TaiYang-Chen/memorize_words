package com.chen.memorizewords.core.network.http

import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>

    sealed class Failure : NetworkResult<Nothing> {
        data class HttpError(
            val code: Int,
            val message: String? = null,
            val businessCode: String? = null,
            val retryAfterSeconds: Long? = null,
            val resetAtMs: Long? = null,
            val serverTimeMs: Long? = null,
            val traceId: String? = null
        ) : Failure()
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
            val result = try {
                val encodedPath = call.request().url.encodedPath
                if (!response.isSuccessful) {
                    ApiResponseParser.httpFailure(
                        code = response.code(),
                        message = response.message(),
                        errorBody = response.errorBody()?.string(),
                        encodedPath = encodedPath
                    )
                } else {
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

                        BodyPolicy.UnitBody ->
                            ApiResponseParser.parseRequired(
                                apiResponse = response.body() as? ApiResponse<R>,
                                encodedPath = encodedPath,
                                successTypeIsUnit = true
                            )
                    }
                }
            } catch (failure: Exception) {
                continuation.resumeWith(Result.failure(failure))
                return
            }
            continuation.resumeWith(Result.success(result))
        }

        override fun onFailure(call: Call<T>, t: Throwable) {
            continuation.resumeWith(Result.success(NetworkResult.Failure.NetworkError(t)))
        }
    })

    continuation.invokeOnCancellation {
        if (continuation.isCancelled) {
            cancel()
        }
    }
}

@Throws(Exception::class)
suspend fun <T> Call<ApiResponse<T>>.awaitApiResponse(
    bodyPolicy: BodyPolicy
): NetworkResult<T> = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback<ApiResponse<T>> {
        override fun onResponse(call: Call<ApiResponse<T>>, response: Response<ApiResponse<T>>) {
            val result: NetworkResult<T> = try {
                val encodedPath = call.request().url.encodedPath
                if (!response.isSuccessful) {
                    ApiResponseParser.httpFailure(
                        code = response.code(),
                        message = response.message(),
                        errorBody = response.errorBody()?.string(),
                        encodedPath = encodedPath
                    )
                } else {
                    @Suppress("UNCHECKED_CAST")
                    when (bodyPolicy) {
                        BodyPolicy.RequireBody -> ApiResponseParser.parseRequired(
                            apiResponse = response.body(),
                            encodedPath = encodedPath,
                            successTypeIsUnit = false
                        )

                        BodyPolicy.AllowNullBody -> ApiResponseParser.parseNullable(
                            apiResponse = response.body() as ApiResponse<T?>?,
                            encodedPath = encodedPath
                        ) as NetworkResult<T>

                        BodyPolicy.UnitBody -> ApiResponseParser.parseRequired(
                            apiResponse = response.body(),
                            encodedPath = encodedPath,
                            successTypeIsUnit = true
                        )
                    }
                }
            } catch (failure: Exception) {
                NetworkResult.Failure.GenericError(
                    "Response processing failed: ${failure.message ?: failure.javaClass.simpleName}"
                )
            }
            continuation.resumeWith(Result.success(result))
        }

        override fun onFailure(call: Call<ApiResponse<T>>, t: Throwable) {
            continuation.resumeWith(Result.success(NetworkResult.Failure.NetworkError(t)))
        }
    })

    continuation.invokeOnCancellation {
        if (continuation.isCancelled) cancel()
    }
}
