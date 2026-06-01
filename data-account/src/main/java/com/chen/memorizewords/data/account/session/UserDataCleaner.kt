package com.chen.memorizewords.data.account.session

import android.content.Context
import androidx.work.WorkManager
import com.chen.memorizewords.domain.account.UserScopedDataResetContributor
import com.tencent.mmkv.MMKV
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserDataCleaner @Inject constructor(
    @ApplicationContext context: Context,
    private val resetContributors: Set<@JvmSuppressWildcards UserScopedDataResetContributor>,
    private val mmkv: MMKV
) {
    private val appContext = context.applicationContext

    suspend fun clearUserLearningData() {
        resetContributors.forEach { contributor ->
            contributor.clearUserScopedData()
        }
        clearUserScopedMmkvState()
    }

    private fun clearUserScopedMmkvState() {
        val keys = mmkv.allKeys() ?: return
        keys.filter(::isUserScopedMmkvKey)
            .forEach { mmkv.removeValueForKey(it) }
    }
}

internal fun isUserScopedMmkvKey(key: String): Boolean {
    return key.startsWith("Session_") ||
        key.startsWith("practice_") ||
        key.startsWith("floating_word_") ||
        key.startsWith("checkin_") ||
        key.startsWith("onboarding_") ||
        key.startsWith("wordbook_")
}
