package com.chen.memorizewords.data.sync.bootstrap

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.chen.memorizewords.data.sync.repository.sync.SyncWorkConstants
import com.chen.memorizewords.domain.sync.ServerBootstrapContributor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataBootstrapCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val contributors: Set<@JvmSuppressWildcards ServerBootstrapContributor>
) {
    private val appContext = context.applicationContext

    fun scheduleBootstrapWork() {
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            SyncWorkConstants.UNIQUE_DATA_BOOTSTRAP,
            DATA_BOOTSTRAP_POLICY,
            buildDataBootstrapRequest()
        )
    }

    fun schedulePostLoginBootstrapWork() {
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            SyncWorkConstants.UNIQUE_POST_LOGIN_BOOTSTRAP,
            POST_LOGIN_BOOTSTRAP_POLICY,
            buildPostLoginBootstrapRequest()
        )
    }

    suspend fun bootstrapMyBooksAndEnqueueDownloads() {
        bootstrapContributors()
    }

    suspend fun syncAfterLoginInOrder() {
        bootstrapContributors()
    }

    private suspend fun bootstrapContributors() {
        contributors
            .sortedBy { it.bootstrapKey }
            .forEach { contributor ->
                contributor.bootstrapFromServer().getOrThrow()
            }
    }

}

internal val DATA_BOOTSTRAP_POLICY = ExistingWorkPolicy.REPLACE
internal val POST_LOGIN_BOOTSTRAP_POLICY = ExistingWorkPolicy.REPLACE

internal fun buildDataBootstrapRequest() =
    OneTimeWorkRequestBuilder<DataBootstrapWorker>()
        .addTag(SyncWorkConstants.TAG_DATA_BOOTSTRAP)
        .build()

internal fun buildPostLoginBootstrapRequest() =
    OneTimeWorkRequestBuilder<PostLoginBootstrapWorker>()
        .addTag(SyncWorkConstants.TAG_POST_LOGIN_BOOTSTRAP)
        .build()
