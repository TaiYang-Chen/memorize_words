package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.sync.remoteapi.dto.wordbook.WordBookDto
import com.chen.memorizewords.domain.sync.model.LearningPrerequisitesSnapshot
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.shop.DownloadState
import com.chen.memorizewords.domain.wordbook.repository.CurrentWordBookLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.shop.RemoteWordBookRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

class LearningPrerequisitesRestorer @Inject constructor(
    private val remoteUserSyncDataSource: RemoteUserSyncDataSource,
    private val studyPlanLocalStatePort: StudyPlanLocalStatePort,
    private val currentWordBookLocalStatePort: CurrentWordBookLocalStatePort,
    private val remoteWordBookRepository: RemoteWordBookRepository
) {

    suspend fun restore(): Result<LearningPrerequisitesSnapshot> = runCatching {
        val studyPlan = remoteUserSyncDataSource.getStudyPlan().getOrThrow()
            ?: error("Remote study plan is missing")
        studyPlanLocalStatePort.overwriteFromRemote(studyPlan)

        val remoteBooks = remoteUserSyncDataSource.getMyWordBooks().getOrThrow()
        val selectedBookId = resolveSelectedBookId(remoteBooks)
        val selectedBook = resolveSelectedBook(selectedBookId, remoteBooks)
            ?: error("Selected word book is missing")

        remoteWordBookRepository.downloadBook(
            book = selectedBook,
            forceRefresh = false,
            runInForeground = false
        )
        waitUntilDownloaded(selectedBookId)

        currentWordBookLocalStatePort.overwriteFromRemote(selectedBookId)
        LearningPrerequisitesSnapshot(
            selectedBookId = selectedBookId,
            studyPlan = studyPlan
        )
    }

    private suspend fun resolveSelectedBookId(remoteBooks: List<WordBookDto>): Long {
        val selectedId = remoteBooks.firstOrNull { it.isSelected }?.id
            ?: remoteUserSyncDataSource.getOnboardingState().getOrThrow()?.selectedWordBookId
        return selectedId?.takeIf { it > 0L }
            ?: error("Remote selected word book is missing")
    }

    private suspend fun resolveSelectedBook(
        selectedBookId: Long,
        remoteBooks: List<WordBookDto>
    ): WordBook? {
        return remoteBooks.firstOrNull { it.id == selectedBookId }?.toDomain()
            ?: remoteWordBookRepository.getShopBookById(selectedBookId)
    }

    private suspend fun waitUntilDownloaded(bookId: Long) {
        withTimeout(DOWNLOAD_TIMEOUT_MS) {
            remoteWordBookRepository.observeDownloadStates()
                .first { states ->
                    when (val state = states[bookId]) {
                        is DownloadState.Downloaded -> true
                        is DownloadState.Failed -> error(state.message)
                        else -> false
                    }
                }
        }
    }
}

private fun WordBookDto.toDomain(): WordBook {
    return WordBook(
        id = id,
        title = title,
        category = category,
        imgUrl = imgUrl,
        description = description,
        totalWords = totalWords,
        contentVersion = contentVersion,
        isNew = isNew,
        isHot = isHot,
        isSelected = isSelected,
        isPublic = isPublic,
        createdByUserId = createdByUserId
    )
}

private const val DOWNLOAD_TIMEOUT_MS = 120_000L
