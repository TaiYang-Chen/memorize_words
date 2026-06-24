package com.chen.memorizewords.domain.account.model.user

import java.io.File

fun User?.avatarLoadSource(): Any? {
    val localPath = this?.localAvatarPath?.trim().orEmpty()
    if (localPath.isNotBlank()) {
        val localFile = File(localPath)
        if (localFile.isFile && localFile.canRead()) {
            return localFile
        }
    }
    return this?.avatarUrl?.trim()?.takeIf { it.isNotBlank() }
}

fun User?.hasReadableLocalAvatar(): Boolean {
    val localPath = this?.localAvatarPath?.trim().orEmpty()
    return localPath.isNotBlank() && File(localPath).isFile && File(localPath).canRead()
}
