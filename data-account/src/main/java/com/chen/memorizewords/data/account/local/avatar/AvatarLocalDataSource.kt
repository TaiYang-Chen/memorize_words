package com.chen.memorizewords.data.account.local.avatar

interface AvatarLocalDataSource {
    fun saveAvatar(userId: Long, imageBytes: ByteArray): String

    fun deleteAvatar(path: String?)
}
