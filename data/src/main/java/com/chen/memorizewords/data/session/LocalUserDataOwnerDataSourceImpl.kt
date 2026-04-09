package com.chen.memorizewords.data.session

import com.tencent.mmkv.MMKV

class LocalUserDataOwnerDataSourceImpl(
    private val mmkv: MMKV
) : LocalUserDataOwnerDataSource {

    private companion object {
        private const val KEY_OWNER_USER_ID = "local_user_data_owner_user_id"
    }

    override fun getOwnerUserId(): Long? {
        val value = mmkv.decodeLong(KEY_OWNER_USER_ID, -1L)
        return if (value == -1L) null else value
    }

    override fun saveOwnerUserId(userId: Long) {
        mmkv.encode(KEY_OWNER_USER_ID, userId)
    }

    override fun clearOwnerUserId() {
        mmkv.removeValueForKey(KEY_OWNER_USER_ID)
    }
}
