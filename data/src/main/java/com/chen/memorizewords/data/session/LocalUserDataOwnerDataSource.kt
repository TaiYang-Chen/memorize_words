package com.chen.memorizewords.data.session

interface LocalUserDataOwnerDataSource {
    fun getOwnerUserId(): Long?
    fun saveOwnerUserId(userId: Long)
    fun clearOwnerUserId()
}
