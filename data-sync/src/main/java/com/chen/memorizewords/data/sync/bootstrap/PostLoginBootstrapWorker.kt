package com.chen.memorizewords.data.sync.bootstrap

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chen.memorizewords.core.network.remote.HttpStatusException
import com.chen.memorizewords.core.network.remote.UnauthorizedNetworkException
import com.chen.memorizewords.data.sync.repository.sync.PostLoginBootstrapStateStore
import com.chen.memorizewords.domain.sync.model.PostLoginBootstrapState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException

class PostLoginBootstrapWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            PostLoginBootstrapWorkerEntryPoint::class.java
        )
        val stateStore = entryPoint.postLoginBootstrapStateStore()
        stateStore.setState(PostLoginBootstrapState.Running)
        return runCatching {
            entryPoint.dataBootstrapCoordinator().syncAfterLoginInOrder()
            stateStore.setState(PostLoginBootstrapState.Succeeded)
            Result.success()
        }.getOrElse { throwable ->
            when (resolvePostLoginBootstrapFailure(throwable)) {
                PostLoginBootstrapFailure.Retry -> Result.retry()
                PostLoginBootstrapFailure.Failed -> {
                    stateStore.setState(PostLoginBootstrapState.Failed)
                    Result.failure()
                }
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PostLoginBootstrapWorkerEntryPoint {
    fun dataBootstrapCoordinator(): DataBootstrapCoordinator
    fun postLoginBootstrapStateStore(): PostLoginBootstrapStateStore
}

internal enum class PostLoginBootstrapFailure {
    Retry,
    Failed
}

internal fun resolvePostLoginBootstrapFailure(throwable: Throwable): PostLoginBootstrapFailure {
    return when {
        throwable is IOException -> PostLoginBootstrapFailure.Retry
        throwable is HttpStatusException && (throwable.code >= 500 || throwable.code == 429) ->
            PostLoginBootstrapFailure.Retry
        throwable is UnauthorizedNetworkException -> PostLoginBootstrapFailure.Failed
        throwable is HttpStatusException -> PostLoginBootstrapFailure.Failed
        else -> PostLoginBootstrapFailure.Failed
    }
}
