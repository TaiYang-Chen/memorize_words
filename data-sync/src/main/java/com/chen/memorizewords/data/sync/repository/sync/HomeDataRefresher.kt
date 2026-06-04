package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.core.network.http.PageData
import com.chen.memorizewords.data.sync.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.sync.remoteapi.api.datasync.DailyStudyDurationDto
import com.chen.memorizewords.data.sync.remoteapi.api.datasync.StudyRecordDto
import com.chen.memorizewords.data.sync.remoteapi.api.datasync.WordBookProgressDto
import com.chen.memorizewords.data.sync.remoteapi.dto.wordbook.WordBookDto
import com.chen.memorizewords.domain.study.model.record.DailyStudyRecords
import com.chen.memorizewords.domain.study.repository.StudyDailyDurationSnapshot
import com.chen.memorizewords.domain.study.repository.StudySnapshotLocalStatePort
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.study.progress.wordbook.WordBookProgress
import com.chen.memorizewords.domain.wordbook.repository.CurrentWordBookLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.WordBookSnapshotLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.shop.RemoteWordBookRepository
import javax.inject.Inject

class HomeDataRefresher @Inject constructor(
    private val remoteUserSyncDataSource: RemoteUserSyncDataSource,
    private val studyPlanLocalStatePort: StudyPlanLocalStatePort,
    private val currentWordBookLocalStatePort: CurrentWordBookLocalStatePort,
    private val wordBookSnapshotLocalStatePort: WordBookSnapshotLocalStatePort,
    private val studySnapshotLocalStatePort: StudySnapshotLocalStatePort,
    private val remoteWordBookRepository: RemoteWordBookRepository
) {

    suspend fun refresh(): Result<Unit> = runCatching {
        studyPlanLocalStatePort.overwriteFromRemote(
            remoteUserSyncDataSource.getStudyPlan().getOrThrow()
        )

        val remoteBooks = remoteUserSyncDataSource.getMyWordBooks().getOrThrow()
        val selectedBookId = resolveSelectedBookId(remoteBooks)
        val selectedBook = resolveSelectedBook(selectedBookId, remoteBooks)
            ?: error("Remote selected word book is missing")
        remoteWordBookRepository.downloadBook(
            book = selectedBook,
            forceRefresh = false,
            runInForeground = false
        )
        currentWordBookLocalStatePort.overwriteFromRemote(selectedBookId)

        wordBookSnapshotLocalStatePort.overwriteProgressFromRemote(
            remoteUserSyncDataSource.getWordBookProgressList().getOrThrow().map { it.toDomain() }
        )

        studySnapshotLocalStatePort.overwriteStudyRecordsFromRemote(
            loadPagedSnapshot { page, count ->
                remoteUserSyncDataSource.getStudyRecords(page = page, count = count)
            }.map { it.toDomain() }
        )
        studySnapshotLocalStatePort.overwriteDailyDurationsFromRemote(
            loadPagedSnapshot { page, count ->
                remoteUserSyncDataSource.getDailyStudyDurations(page = page, count = count)
            }.map { it.toSnapshot() }
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
}

private suspend fun <T> loadPagedSnapshot(
    pageSize: Int = HOME_DATA_PAGE_SIZE,
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

private fun StudyRecordDto.toDomain(): DailyStudyRecords {
    return DailyStudyRecords(
        date = date,
        wordId = wordId,
        word = word,
        definition = definition,
        isNewWord = isNewWord
    )
}

private fun DailyStudyDurationDto.toSnapshot(): StudyDailyDurationSnapshot {
    return StudyDailyDurationSnapshot(
        date = date,
        totalDurationMs = totalDurationMs,
        updatedAt = updatedAt,
        isNewPlanCompleted = isNewPlanCompleted,
        isReviewPlanCompleted = isReviewPlanCompleted
    )
}

private const val HOME_DATA_PAGE_SIZE = 100
