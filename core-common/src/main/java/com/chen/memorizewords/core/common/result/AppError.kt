package com.chen.memorizewords.core.common.result

sealed interface AppError {
    val message: String?

    data class Network(
        val code: Int? = null,
        override val message: String? = null,
        val cause: Throwable? = null
    ) : AppError

    data class Unauthorized(
        override val message: String? = null
    ) : AppError

    data class Validation(
        val field: String? = null,
        override val message: String? = null
    ) : AppError

    data class Storage(
        override val message: String? = null,
        val cause: Throwable? = null
    ) : AppError

    data class Unknown(
        override val message: String? = null,
        val cause: Throwable? = null
    ) : AppError
}
