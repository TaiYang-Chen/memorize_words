package com.chen.memorizewords.data.account.session

interface LocalUserDataOwnerDataSource {
    fun getOwnerUserId(): Long?
    fun saveOwnerUserId(userId: Long)
    fun clearOwnerUserId()
}
