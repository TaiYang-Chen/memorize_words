package com.chen.memorizewords.data.account.session

import android.content.Context
import android.util.Base64
import com.tencent.mmkv.MMKV
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TokenLocalDataSource(private val mmkv: MMKV) : SessionLocalDataSource {

    private val keyAccess = "key_access_token"
    private val keyRefresh = "key_refresh_token"
    private val keyExpires = "key_expires_at"
    private val sessionFlow = MutableStateFlow(getSession())

    override fun saveSession(session: AuthSession) {
        mmkv.encode(keyAccess, TokenCipher.encrypt(session.accessToken))
        mmkv.encode(keyRefresh, TokenCipher.encrypt(session.refreshToken))
        mmkv.encode(keyExpires, session.expiresAt)
        sessionFlow.value = session
    }

    override fun getSession(): AuthSession? {
        val access = mmkv.decodeString(keyAccess)?.let(TokenCipher::decryptCompat) ?: return null
        val refresh = mmkv.decodeString(keyRefresh)?.let(TokenCipher::decryptCompat) ?: return null
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

private object TokenCipher {
    private const val KEY_ALIAS = "memorize_words_session_tokens"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "v1:"
    private const val GCM_TAG_BITS = 128

    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        return PREFIX + Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    fun decryptCompat(value: String): String? {
        if (!value.startsWith(PREFIX)) return value
        return runCatching {
            val packed = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
            val iv = packed.copyOfRange(0, 12)
            val encrypted = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance("AES", ANDROID_KEY_STORE)
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
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
