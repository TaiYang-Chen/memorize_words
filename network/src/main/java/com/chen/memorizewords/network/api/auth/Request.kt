package com.chen.memorizewords.network.api.auth

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class LoginRequest(
    val loginMethod: String = "password",
    val emailOrPhone: String? = null,
    val phone: String? = null,
    val password: String? = null,
    val smsCode: String? = null,
    val oauthCode: String? = null,
    val platform: String? = null,
    val state: String? = null
)

@JsonClass(generateAdapter = false)
data class RegisterRequest(
    val phone: String,
    val password: String,
    val registerMethod: String = "password"
)

@JsonClass(generateAdapter = false)
data class SendSmsCodeRequest(
    val phone: String,
    val scene: String = "login"
)

@JsonClass(generateAdapter = false)
data class RefreshRequest(val refreshToken: String)

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
