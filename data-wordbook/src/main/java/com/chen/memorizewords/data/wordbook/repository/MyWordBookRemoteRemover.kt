package com.chen.memorizewords.data.wordbook.repository

import com.chen.memorizewords.data.wordbook.remote.datasync.RemoteUserSyncDataSource
import javax.inject.Inject

interface MyWordBookRemoteRemover {
    suspend fun removeMyWordBook(bookId: Long): Result<Unit>
}

class RemoteUserSyncMyWordBookRemoteRemover @Inject constructor(
    private val remoteUserSyncDataSource: RemoteUserSyncDataSource
) : MyWordBookRemoteRemover {
    override suspend fun removeMyWordBook(bookId: Long): Result<Unit> {
        return remoteUserSyncDataSource.removeMyWordBook(bookId)
    }
}
