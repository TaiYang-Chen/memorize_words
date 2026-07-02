package com.chen.memorizewords.data.sync.local.mmkv.appupdate

import com.chen.memorizewords.domain.sync.appupdate.AppUpdateDismissRecord
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateCachedForceUpdate
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateInfo
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateLocalStateRepository
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateLocalStateStore @Inject constructor(
    private val mmkv: MMKV,
    private val gson: Gson
) : AppUpdateLocalStateRepository {
    override fun getOrCreateInstallId(): String {
        val existing = mmkv.decodeString(KEY_INSTALL_ID)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        mmkv.encode(KEY_INSTALL_ID, created)
        return created
    }

    override fun getDismissRecord(): AppUpdateDismissRecord? {
        return mmkv.decodeString(KEY_DISMISS_RECORD)
            ?.let { json -> runCatching { gson.fromJson(json, AppUpdateDismissRecord::class.java) }.getOrNull() }
    }

    override fun setDismissed(releaseId: Long, dismissedAtMillis: Long) {
        mmkv.encode(KEY_DISMISS_RECORD, gson.toJson(AppUpdateDismissRecord(releaseId, dismissedAtMillis)))
    }

    override fun getCachedForceUpdate(): AppUpdateCachedForceUpdate? {
        val json = mmkv.decodeString(KEY_FORCE_UPDATE) ?: return null
        return runCatching {
            gson.fromJson(json, AppUpdateCachedForceUpdate::class.java)
                .takeIf { it.info.forceUpdate }
        }.getOrNull()
            ?: runCatching {
                gson.fromJson(json, AppUpdateInfo::class.java)
                    .takeIf { it.forceUpdate }
                    ?.let { AppUpdateCachedForceUpdate(info = it, cachedAtMillis = 0L) }
            }.getOrNull()
    }

    override fun setCachedForceUpdate(info: AppUpdateInfo?, cachedAtMillis: Long) {
        if (info == null) {
            mmkv.removeValueForKey(KEY_FORCE_UPDATE)
        } else {
            mmkv.encode(
                KEY_FORCE_UPDATE,
                gson.toJson(AppUpdateCachedForceUpdate(info = info, cachedAtMillis = cachedAtMillis))
            )
        }
    }

    private companion object {
        const val KEY_INSTALL_ID = "app_update_install_id"
        const val KEY_DISMISS_RECORD = "app_update_dismiss_record"
        const val KEY_FORCE_UPDATE = "app_update_cached_force_update"
    }
}
