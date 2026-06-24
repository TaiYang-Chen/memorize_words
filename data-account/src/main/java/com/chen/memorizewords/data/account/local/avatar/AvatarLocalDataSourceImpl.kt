package com.chen.memorizewords.data.account.local.avatar

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvatarLocalDataSourceImpl @Inject constructor(
    @ApplicationContext context: Context
) : AvatarLocalDataSource {

    private val avatarDir = File(context.applicationContext.filesDir, "avatar")

    override fun saveAvatar(userId: Long, imageBytes: ByteArray): String {
        require(userId > 0) { "User id is required to cache avatar" }
        require(imageBytes.isNotEmpty()) { "Avatar image is empty" }
        if (!avatarDir.exists()) {
            avatarDir.mkdirs()
        }
        val target = File(avatarDir, "user_$userId.jpg")
        val temp = File(avatarDir, "user_$userId.tmp")
        temp.writeBytes(imageBytes)
        if (target.exists()) {
            target.delete()
        }
        check(temp.renameTo(target)) { "Failed to cache avatar" }
        return target.absolutePath
    }

    override fun deleteAvatar(path: String?) {
        val source = path?.trim().orEmpty()
        if (source.isBlank()) return
        runCatching {
            val file = File(source)
            val canonicalDir = avatarDir.canonicalFile
            val canonicalFile = file.canonicalFile
            if (canonicalFile.parentFile == canonicalDir && canonicalFile.exists()) {
                canonicalFile.delete()
            }
        }
    }
}
