package com.chen.memorizewords.data.session

import android.content.Context
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TokenLocalDataSource(private val mmkv: MMKV) : SessionLocalDataSource {

    private val keyAccess = "key_access_token"
    private val keyRefresh = "key_refresh_token"
    private val keyExpires = "key_expires_at"
    private val sessionFlow = MutableStateFlow(getSession())

    override fun saveSession(session: AuthSession) {
        mmkv.encode(keyAccess, session.accessToken)
        mmkv.encode(keyRefresh, session.refreshToken)
        mmkv.encode(keyExpires, session.expiresAt)
        sessionFlow.value = session
    }

    override fun getSession(): AuthSession? {
        val access = mmkv.decodeString(keyAccess) ?: return null
        val refresh = mmkv.decodeString(keyRefresh) ?: return null
        val expires = mmkv.decodeLong(keyExpires, 0L)
        return AuthSession(accessToken = access, refreshToken = refresh, expiresAt = expires)
    }

    override fun observeSession(): Flow<AuthSession?> = sessionFlow.asStateFlow()

    override fun clear() {
        mmkv.removeValueForKey(keyAccess)
        mmkv.removeValueForKey(keyRefresh)
        mmkv.removeValueForKey(keyExpires)
        sessionFlow.value = null
    }
}

object MMKVInitializer {
    private var initialized = false

    fun initialize(context: Context) {
        if (!initialized) {
            MMKV.initialize(context)
            initialized = true
        }
    }
}
