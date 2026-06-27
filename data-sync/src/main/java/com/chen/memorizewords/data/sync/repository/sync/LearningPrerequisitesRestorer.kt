package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.core.network.http.PageData
import com.chen.memorizewords.data.sync.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.sync.remoteapi.api.datasync.WordBookProgressDto
import com.chen.memorizewords.data.sync.remoteapi.dto.wordbook.WordBookDto
import com.chen.memorizewords.data.sync.remoteapi.dto.wordstate.WordStateDto
import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import com.chen.memorizewords.domain.study.repository.StudySnapshotLocalStatePort
import com.chen.memorizewords.domain.sync.model.LearningPrerequisitesSnapshot
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.shop.DownloadState
import com.chen.memorizewords.domain.wordbook.model.study.progress.wordbook.WordBookProgress
import com.chen.memorizewords.domain.wordbook.repository.CurrentWordBookLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.WordBookLearningStateSnapshot
import com.chen.memorizewords.domain.wordbook.repository.WordBookSnapshotLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.shop.RemoteWordBookRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

class LearningPrerequisitesRestorer @Inject constructor(
    private val remoteUserSyncDataSource: RemoteUserSyncDataSource,
    private val studyPlanLocalStatePort: StudyPlanLocalStatePort,
    private val currentWordBookLocalStatePort: CurrentWordBookLocalStatePort,
    private val remoteWordBookRepository: RemoteWordBookRepository,
    private val studySnapshotLocalStatePort: StudySnapshotLocalStatePort,
    private val wordBookSnapshotLocalStatePort: WordBookSnapshotLocalStatePort
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

        val states = loadPagedSnapshot { page, count ->
            remoteUserSyncDataSource.getWordStates(
                bookId = selectedBookId,
                page = page,
                count = count
            )
        }
        studySnapshotLocalStatePort.overwriteLearningStatesForBookFromRemote(
            bookId = selectedBookId,
            states = states.map { it.toDomain(selectedBookId) }
        )
        wordBookSnapshotLocalStatePort.overwriteLearningStatesForBookFromRemote(
            bookId = selectedBookId,
            states = states.map { it.toSnapshot(selectedBookId) }
        )

        val currentProgress = remoteUserSyncDataSource.getWordBookProgressList()
            .getOrThrow()
            .filter { it.bookId == selectedBookId }
            .map { it.toDomain() }
        wordBookSnapshotLocalStatePort.upsertProgressFromRemote(currentProgress)

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

    private suspend fun <T> loadPagedSnapshot(
        pageSize: Int = DEFAULT_PAGE_SIZE,
        loader: suspend (page: Int, count: Int) -> Result<PageData<T>>
    ): List<T> {
        val items = mutableListOf<T>()
        var page = 0
        var loaded = 0
        while (true) {
            val pageData = loader(page, pageSize).getOrThrow()
            if (pageData.items.isEmpty()) break
            items += pageData.items
            loaded += pageData.items.size
            if (pageData.total > 0 && loaded.toLong() >= pageData.total) {
                break
            }
            page++
        }
        return items
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

private fun WordStateDto.toDomain(fallbackBookId: Long): WordLearningState {
    return WordLearningState(
        wordId = wordId,
        bookId = if (bookId > 0L) bookId else fallbackBookId,
        totalLearnCount = totalLearnCount,
        lastLearnTime = lastLearnTime,
        nextReviewTime = nextReviewTime,
        masteryLevel = masteryLevel,
        userStatus = userStatus,
        repetition = repetition,
        interval = interval,
        efactor = efactor
    )
}

private fun WordStateDto.toSnapshot(fallbackBookId: Long): WordBookLearningStateSnapshot {
    return WordBookLearningStateSnapshot(
        wordId = wordId,
        bookId = if (bookId > 0L) bookId else fallbackBookId,
        totalLearnCount = totalLearnCount,
        lastLearnTime = lastLearnTime,
        nextReviewTime = nextReviewTime,
        masteryLevel = masteryLevel,
        userStatus = userStatus,
        repetition = repetition,
        interval = interval,
        efactor = efactor
    )
}

private fun WordBookProgressDto.toDomain(): WordBookProgress {
    return WordBookProgress(
        wordBookId = bookId,
        wordBookName = bookName,
        learningCount = learnedCount,
        masteredCount = masteredCount,
        totalCount = totalCount,
        correctCount = correctCount,
        wrongCount = wrongCount,
        studyDayCount = studyDayCount,
        lastStudyDate = lastStudyDate
    )
}

private const val DEFAULT_PAGE_SIZE = 100

private const val DOWNLOAD_TIMEOUT_MS = 120_000L
