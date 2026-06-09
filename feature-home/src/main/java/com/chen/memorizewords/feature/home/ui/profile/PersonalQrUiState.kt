package com.chen.memorizewords.feature.home.ui.profile

data class PersonalQrUiState(
    val userId: Long = 0L,
    val nickname: String = "",
    val avatarUrl: String? = null,
    val payload: String = PersonalQrPayload.create(0L, "")
)
