package com.chen.memorizewords.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.chen.memorizewords.data.local.mmkv.wordbookupdate.WordBookUpdateSettingsStore
import com.chen.memorizewords.data.local.room.wordbook.WordBookSyncStateStore
import com.chen.memorizewords.data.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.repository.wordbook.update.CurrentWordBookUpdateWorkConstants
import com.chen.memorizewords.data.repository.wordbook.update.CurrentWordBookUpdateWorker
import com.chen.memorizewords.domain.model.wordbook.WordBookPendingUpdate
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateAction
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateApplyMode
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateCandidate
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateExecutionMode
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateImportance
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateJobState
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateSettings
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateSummary
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateTrigger
import com.chen.memorizewords.domain.repository.WordBookUpdateRepository
import com.chen.memorizewords.network.api.datasync.WordBookUpdateActionRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class WordBookUpdateRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val remoteUserSyncDataSource: RemoteUserSyncDataSource,
    private val syncStateStore: WordBookSyncStateStore,
    private val settingsStore: WordBookUpdateSettingsStore
) : WordBookUpdateRepository {

    private val appContext = context.applicationContext

    override suspend fun fetchCandidate(trigger: WordBookUpdateTrigger): Result<WordBookUpdateCandidate?> {
        val result = remoteUserSyncDataSource.getCurrentWordBookUpdateCandidate(trigger.name.lowercase())
        return withContext(Dispatchers.IO) {
            result.map { dto ->
                dto?.toDomain()?.also { candidate ->
                    syncStateStore.setLastCheckedAt(candidate.bookId, System.currentTimeMillis())
                    syncStateStore.updateRemoteVersions(mapOf(candidate.bookId to candidate.targetVersion))
                    syncStateStore.setPendingTargetVersion(candidate.bookId, candidate.targetVersion)
                }?.takeUnless { candidate ->
                    val localVersion = syncStateStore.getLocalVersion(candidate.bookId)
                    val ignoredVersion = syncStateStore.getIgnoredVersion(candidate.bookId)
                    candidate.targetVersion <= localVersion || candidate.targetVersion <= ignoredVersion
                }
            }
        }
    }

    override suspend fun reportAction(
        action: WordBookUpdateAction,
        candidate: WordBookUpdateCandidate?,
        trigger: WordBookUpdateTrigger?,
        executionMode: WordBookUpdateExecutionMode?,
        message: String?,
        deferredUntil: Long?
    ): Result<Unit> {
        val request = WordBookUpdateActionRequest(
            action = action.name,
            bookId = candidate?.bookId,
            targetVersion = candidate?.targetVersion,
            trigger = trigger?.name?.lowercase(),
            executionMode = executionMode?.name,
            deferredUntil = deferredUntil,
            failureReason = message
        )
        return remoteUserSyncDataSource.reportCurrentWordBookUpdateAction(request)
    }

    override suspend fun saveDeferredUntil(bookId: Long, deferredUntil: Long) {
        syncStateStore.setDeferredUntil(bookId, deferredUntil)
    }

    override suspend fun ignoreVersion(bookId: Long, targetVersion: Long): Result<Unit> {
        return remoteUserSyncDataSource.reportCurrentWordBookUpdateAction(
            WordBookUpdateActionRequest(
                action = WordBookUpdateAction.IGNORE_VERSION.name,
                bookId = bookId,
                targetVersion = targetVersion
            )
        ).onSuccess {
            syncStateStore.setIgnoredVersion(bookId, targetVersion)
            syncStateStore.clearPendingTarget(bookId)
        }
    }

    override suspend fun enqueueUpdate(
        bookId: Long,
        targetVersion: Long,
        executionMode: WordBookUpdateExecutionMode
    ) {
        val workManager = workManagerOrNull() ?: return
        val request = OneTimeWorkRequestBuilder<CurrentWordBookUpdateWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    CurrentWordBookUpdateWorkConstants.KEY_BOOK_ID to bookId,
                    CurrentWordBookUpdateWorkConstants.KEY_TARGET_VERSION to targetVersion,
                    CurrentWordBookUpdateWorkConstants.KEY_EXECUTION_MODE to executionMode.name
                )
            )
            .addTag(CurrentWordBookUpdateWorkConstants.TAG_UPDATE)
            .addTag(CurrentWordBookUpdateWorkConstants.bookTag(bookId))
            .build()
        workManager.enqueueUniqueWork(
            CurrentWordBookUpdateWorkConstants.uniqueWorkName(bookId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override suspend fun getSettings(): WordBookUpdateSettings = settingsStore.get()

    override fun observeSettings(): Flow<WordBookUpdateSettings> = settingsStore.observe()

    override suspend fun saveSettings(settings: WordBookUpdateSettings) {
        settingsStore.save(settings)
    }

    override fun observeUpdateJobState(bookId: Long): Flow<WordBookUpdateJobState> {
        val workManager = workManagerOrNull() ?: return flowOf(WordBookUpdateJobState.Idle)
        return workManager.getWorkInfosByTagFlow(CurrentWordBookUpdateWorkConstants.bookTag(bookId))
            .map { infos -> infos.toJobState(bookId) }
    }

    override suspend fun getPendingUpdate(bookId: Long): Result<WordBookPendingUpdate?> {
        val result = remoteUserSyncDataSource.getPendingWordBookUpdate(bookId)
        return withContext(Dispatchers.IO) {
            result.map { dto ->
                syncStateStore.setLastCheckedAt(bookId, System.currentTimeMillis())
                if (dto == null) {
                    return@map null
                }
                syncStateStore.updateRemoteVersions(mapOf(bookId to dto.targetVersion))
                val localVersion = syncStateStore.getLocalVersion(bookId)
                val ignoredVersion = syncStateStore.getIgnoredVersion(bookId)
                if (dto.targetVersion <= localVersion || dto.targetVersion <= ignoredVersion) {
                    null
                } else {
                    dto.toLegacyDomain()
                }
            }
        }
    }

    override suspend fun ignoreUpdate(bookId: Long, targetVersion: Long): Result<Unit> =
        ignoreVersion(bookId, targetVersion)

    override suspend fun markPrompted(bookId: Long, targetVersion: Long) {
        syncStateStore.setLastPrompt(bookId, targetVersion, System.currentTimeMillis(), WordBookUpdateTrigger.FOREGROUND)
    }

    private fun workManagerOrNull(): WorkManager? {
        return runCatching { WorkManager.getInstance(appContext) }.getOrNull()
    }

    private fun List<WorkInfo>.toJobState(bookId: Long): WordBookUpdateJobState {
        val activeInfo = firstOrNull {
            it.state == WorkInfo.State.RUNNING ||
                it.state == WorkInfo.State.ENQUEUED ||
                it.state == WorkInfo.State.BLOCKED
        }
        if (activeInfo != null) {
            return WordBookUpdateJobState.Running(
                bookId = bookId,
                targetVersion = activeInfo.progress.getLong(
                    CurrentWordBookUpdateWorkConstants.KEY_TARGET_VERSION,
                    0L
                ),
                progress = activeInfo.progress.getInt(
                    CurrentWordBookUpdateWorkConstants.KEY_PROGRESS,
                    0
                ).coerceIn(0, 100)
            )
        }

        val successInfo = firstOrNull { it.state == WorkInfo.State.SUCCEEDED }
        if (successInfo != null) {
            return WordBookUpdateJobState.Succeeded(
                bookId = successInfo.outputData.getLong(CurrentWordBookUpdateWorkConstants.KEY_BOOK_ID, bookId),
                targetVersion = successInfo.outputData.getLong(
                    CurrentWordBookUpdateWorkConstants.KEY_TARGET_VERSION,
                    0L
                )
            )
        }

        val failedInfo = firstOrNull { it.state == WorkInfo.State.FAILED }
        if (failedInfo != null) {
            return WordBookUpdateJobState.Failed(
                bookId = failedInfo.outputData.getLong(CurrentWordBookUpdateWorkConstants.KEY_BOOK_ID, bookId),
                targetVersion = failedInfo.outputData.getLong(
                    CurrentWordBookUpdateWorkConstants.KEY_TARGET_VERSION,
                    0L
                ),
                message = failedInfo.outputData.getString(
                    CurrentWordBookUpdateWorkConstants.KEY_ERROR_MESSAGE
                ) ?: "Update failed"
            )
        }

        return WordBookUpdateJobState.Idle
    }
}

private fun com.chen.memorizewords.network.api.datasync.WordBookUpdateCandidateDto.toDomain(): WordBookUpdateCandidate {
    return WordBookUpdateCandidate(
        bookId = bookId,
        bookName = bookName,
        currentVersion = currentVersion,
        targetVersion = targetVersion,
        publishedAt = publishedAt,
        summary = WordBookUpdateSummary(
            addedCount = summary.addedCount,
            modifiedCount = summary.modifiedCount,
            removedCount = summary.removedCount,
            sampleWords = summary.sampleWords
        ),
        applyMode = runCatching { WordBookUpdateApplyMode.valueOf(applyMode) }
            .getOrDefault(WordBookUpdateApplyMode.FULL),
        importance = runCatching { WordBookUpdateImportance.valueOf(importance) }
            .getOrDefault(WordBookUpdateImportance.NORMAL),
        detailAvailable = detailAvailable,
        estimatedDownloadBytes = estimatedDownloadBytes,
        forcePrompt = forcePrompt,
        silentAllowed = silentAllowed
    )
}

private fun com.chen.memorizewords.network.api.datasync.PendingWordBookUpdateDto.toLegacyDomain(): WordBookPendingUpdate {
    return WordBookPendingUpdate(
        bookId = bookId,
        bookName = bookName,
        currentVersion = currentVersion,
        targetVersion = targetVersion,
        publishedAt = publishedAt,
        summary = WordBookUpdateSummary(
            addedCount = summary.addedCount,
            modifiedCount = summary.modifiedCount,
            removedCount = summary.removedCount,
            sampleWords = summary.sampleWords
        ),
        applyMode = runCatching { WordBookUpdateApplyMode.valueOf(applyMode) }
            .getOrDefault(WordBookUpdateApplyMode.FULL)
    )
}
