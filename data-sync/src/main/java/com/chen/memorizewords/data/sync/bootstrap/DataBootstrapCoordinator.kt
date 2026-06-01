package com.chen.memorizewords.data.sync.bootstrap

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
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
        val request = OneTimeWorkRequestBuilder<DataBootstrapWorker>()
            .setConstraints(networkConstraints())
            .addTag(SyncWorkConstants.TAG_DATA_BOOTSTRAP)
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            SyncWorkConstants.UNIQUE_DATA_BOOTSTRAP,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun schedulePostLoginBootstrapWork() {
        val request = OneTimeWorkRequestBuilder<PostLoginBootstrapWorker>()
            .setConstraints(networkConstraints())
            .addTag(SyncWorkConstants.TAG_POST_LOGIN_BOOTSTRAP)
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            SyncWorkConstants.UNIQUE_POST_LOGIN_BOOTSTRAP,
            ExistingWorkPolicy.KEEP,
            request
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

    private fun networkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
