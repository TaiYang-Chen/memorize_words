package com.chen.memorizewords.data.account.remoteapi.api.auth

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class LoginRequest(
    val loginMethod: String = "password",
    val emailOrPhone: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val password: String? = null,
    val emailCode: String? = null,
    val oauthCode: String? = null,
    val platform: String? = null,
    val state: String? = null,
    val cancelDeletion: Boolean = false
)

@JsonClass(generateAdapter = false)
data class RegisterRequest(
    val email: String? = null,
    val emailCode: String? = null,
    val password: String,
    val registerMethod: String = "email"
)

@JsonClass(generateAdapter = false)
data class SendEmailCodeRequest(
    val email: String,
    val scene: String = "login"
)

@JsonClass(generateAdapter = false)
data class RefreshRequest(val refreshToken: String)

@JsonClass(generateAdapter = false)
data class FusionLoginRequest(
    val verifyToken: String,
    val cancelDeletion: Boolean = false
)

@JsonClass(generateAdapter = false)
data class FusionRegisterRequest(
    val verifyToken: String,
    val password: String
)

@JsonClass(generateAdapter = false)
data class FusionAuthTokenDto(
    val authToken: String,
    val schemeCode: String,
    val expiresIn: Int
)

@JsonClass(generateAdapter = false)
data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String
)

@JsonClass(generateAdapter = false)
data class BindSocialRequest(
    val platform: String,
    val oauthCode: String,
    val state: String? = null
)

@JsonClass(generateAdapter = false)
data class BindEmailRequest(
    val email: String,
    val emailCode: String
)

@JsonClass(generateAdapter = false)
data class BindPhoneRequest(val verifyToken: String)

@JsonClass(generateAdapter = false)
data class AvatarUploadDto(
    val url: String
)

data class ProfilePatchRequest(
    val field: Field,
    val value: String
) {
    enum class Field(val key: String) {
        NICKNAME("nickname"),
        GENDER("gender"),
        PHONE("phone"),
        WECHAT("wechat"),
        QQ("qq"),
        AVATAR_URL("avatarUrl")
    }

    fun toRequest(): Map<String, String> = mapOf(field.key to value)
}
