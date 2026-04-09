package com.chen.memorizewords.data.local.mmkv.auth

import android.icu.number.NumberFormatter
import com.chen.memorizewords.domain.model.user.User
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthLocalDataSourceImpl(private val mmkv: MMKV) : AuthLocalDataSource {

    private val userFlow = MutableStateFlow(getUser())

    private companion object {
        private const val USER_ID = "user_id"
        private const val EMAIL = "email"
        private const val NICKNAME = "nickname"
        private const val GENDER = "gender"
        private const val AVATAR_URL = "avatar_url"
        private const val PHONE = "phone"
        private const val QQ = "qq"
        private const val WECHAT = "wechat"
        private const val EMAIL_VERIFIED = "email_verified"

        private val KEYS = arrayOf(
            USER_ID, EMAIL, NICKNAME,
            AVATAR_URL, PHONE, QQ, WECHAT, EMAIL_VERIFIED
        )
    }

    override fun getUser(): User? {
        val userId = mmkv.getLong(USER_ID, -1L)
        if (userId == -1L) return null

        return User(
            userId = userId,
            email = mmkv.getString(EMAIL, null),
            nickname = mmkv.getString(NICKNAME, null),
            gender = mmkv.getString(GENDER, null),
            avatarUrl = mmkv.getString(AVATAR_URL, null),
            phone = mmkv.getString(PHONE, null),
            qq = mmkv.getString(QQ, null),
            wechat = mmkv.getString(WECHAT, null),
            emailVerified = mmkv.getBoolean(EMAIL_VERIFIED, false)
        )
    }

    override fun getUserFlow(): Flow<User?> {
        return userFlow.asStateFlow()
    }

    override fun getUserId(): Long? {
        val id = mmkv.getLong(USER_ID, -1L)
        return if (id == -1L) null else id
    }

    override fun saveUser(user: User) {
        with(mmkv) {
            putLong(USER_ID, user.userId)
            putString(EMAIL, user.email)
            putString(NICKNAME, user.nickname)
            putString(GENDER, user.gender)
            putString(AVATAR_URL, user.avatarUrl)
            putString(PHONE, user.phone)
            putString(QQ, user.qq)
            putString(WECHAT, user.wechat)
            putBoolean(EMAIL_VERIFIED, user.emailVerified)
            commit()
        }
        userFlow.value = user
    }

    override fun clear() {
        KEYS.forEach { mmkv.removeValueForKey(it) }
        mmkv.commit()
        userFlow.value = null
    }
}
