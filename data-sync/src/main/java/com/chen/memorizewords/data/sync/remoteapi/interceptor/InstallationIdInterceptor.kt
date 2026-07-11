package com.chen.memorizewords.data.sync.remoteapi.interceptor

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class InstallationIdProvider @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun get(): String {
        preferences.getString(KEY_INSTALLATION_ID, null)
            ?.takeIf(::isUuid)
            ?.let { return it }
        return UUID.randomUUID().toString().also { generated ->
            preferences.edit().putString(KEY_INSTALLATION_ID, generated).apply()
        }
    }

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess

    private companion object {
        const val PREFERENCES_NAME = "memorize_installation"
        const val KEY_INSTALLATION_ID = "installation_id"
    }
}

@Singleton
class InstallationIdInterceptor @Inject constructor(
    private val provider: InstallationIdProvider
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header(HEADER_INSTALLATION_ID, provider.get())
            .build()
        return chain.proceed(request)
    }

    companion object {
        const val HEADER_INSTALLATION_ID = "X-Install-Id"
    }
}
