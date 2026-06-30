package com.chen.memorizewords.data.account.remoteapi.api.auth

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class LoginRequest(
    val loginMethod: String = "password",
    val identifierType: String? = null,
    val identifier: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val password: String? = null,
    val emailCode: String? = null,
    val verifyToken: String? = null,
    val cancelDeletion: Boolean = false
)

@JsonClass(generateAdapter = false)
data class RegisterRequest(
    val account: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val emailCode: String? = null,
    val verifyToken: String? = null,
    val password: String? = null,
    val registerMethod: String
)

@JsonClass(generateAdapter = false)
data class SendEmailCodeRequest(
    val email: String,
    val scene: String = "login"
)

@JsonClass(generateAdapter = false)
data class RefreshRequest(val refreshToken: String)

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
