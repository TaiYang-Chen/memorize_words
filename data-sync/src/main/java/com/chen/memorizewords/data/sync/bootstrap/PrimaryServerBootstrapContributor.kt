package com.chen.memorizewords.data.sync.bootstrap

import com.chen.memorizewords.core.common.calendar.CheckInConfigDataSource
import com.chen.memorizewords.data.sync.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.sync.remote.learningsync.RemoteLearningSyncDataSource
import com.chen.memorizewords.domain.floating.FloatingSnapshotLocalStatePort
import com.chen.memorizewords.domain.floating.FloatingSettingsLocalStatePort
import com.chen.memorizewords.domain.floating.model.FloatingWordDisplayRecord
import com.chen.memorizewords.domain.practice.PracticeDailyDurationSnapshot
import com.chen.memorizewords.domain.practice.PracticeSessionRecord
import com.chen.memorizewords.domain.practice.PracticeSnapshotLocalStatePort
import com.chen.memorizewords.domain.practice.PracticeSettingsLocalStatePort
import com.chen.memorizewords.domain.study.model.favorites.WordFavorites
import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.model.record.CheckInType
import com.chen.memorizewords.domain.study.model.record.DailyStudyRecords
import com.chen.memorizewords.domain.study.repository.StudyDailyDurationSnapshot
import com.chen.memorizewords.domain.study.repository.StudySnapshotLocalStatePort
import com.chen.memorizewords.domain.sync.ServerBootstrapContributor
import com.chen.memorizewords.domain.wordbook.model.study.progress.wordbook.WordBookProgress
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.repository.CurrentWordBookLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.WordBookLearningStateSnapshot
import com.chen.memorizewords.domain.wordbook.repository.WordBookSnapshotLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.onboarding.OnboardingRepository
import com.chen.memorizewords.domain.wordbook.repository.shop.RemoteWordBookRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrimaryServerBootstrapContributor @Inject constructor(
    private val remoteUserSyncDataSource: RemoteUserSyncDataSource,
    private val remoteLearningSyncDataSource: RemoteLearningSyncDataSource,
    private val onboardingRepository: OnboardingRepository,
    private val studyPlanLocalStatePort: StudyPlanLocalStatePort,
    private val practiceSettingsLocalStatePort: PracticeSettingsLocalStatePort,
    private val floatingSettingsLocalStatePort: FloatingSettingsLocalStatePort,
    private val studySnapshotLocalStatePort: StudySnapshotLocalStatePort,
    private val practiceSnapshotLocalStatePort: PracticeSnapshotLocalStatePort,
    private val floatingSnapshotLocalStatePort: FloatingSnapshotLocalStatePort,
    private val wordBookSnapshotLocalStatePort: WordBookSnapshotLocalStatePort,
    private val currentWordBookLocalStatePort: CurrentWordBookLocalStatePort,
    private val remoteWordBookRepository: RemoteWordBookRepository,
    private val checkInConfigDataSource: CheckInConfigDataSource
) : ServerBootstrapContributor {

    override val bootstrapKey: String = "primary_server_snapshot"

    override suspend fun bootstrapFromServer(): Result<Unit> = runCatching {
        val onboardingSnapshot = remoteUserSyncDataSource.getOnboardingState().getOrThrow()
        onboardingRepository.replaceCurrentSnapshot(onboardingSnapshot)

        studyPlanLocalStatePort.overwriteFromRemote(
            remoteUserSyncDataSource.getStudyPlan().getOrThrow()
        )

        remoteLearningSyncDataSource.getPracticeSettings().getOrThrow()?.let {
            practiceSettingsLocalStatePort.overwriteFromRemote(it)
        } ?: practiceSettingsLocalStatePort.clearLocalState()

        remoteLearningSyncDataSource.getFloatingSettings().getOrThrow()?.let {
            floatingSettingsLocalStatePort.overwriteFromRemote(it)
        } ?: floatingSettingsLocalStatePort.clearLocalState()

        remoteUserSyncDataSource.getCheckInConfig().getOrThrow()?.let { config ->
            checkInConfigDataSource.saveDayBoundaryOffsetMinutes(config.dayBoundaryOffsetMinutes)
            checkInConfigDataSource.saveTimezoneId(config.timezoneId)
        }
        remoteUserSyncDataSource.getCheckInStatus().getOrThrow()?.let { status ->
            checkInConfigDataSource.saveCachedMakeupCardBalance(status.makeupCardBalance)
            checkInConfigDataSource.saveLastCheckInSyncAt(System.currentTimeMillis())
        }

        val remoteBooks = remoteUserSyncDataSource.getMyWordBooks().getOrThrow()
        val selectedBookId = remoteBooks.firstOrNull { it.isSelected }?.id
            ?: onboardingSnapshot?.selectedWordBookId

        remoteBooks.forEach { dto ->
            remoteWordBookRepository.downloadBook(
                book = dto.toDomain(),
                forceRefresh = false,
                runInForeground = false
            )
        }

        remoteBooks.forEach { book ->
            val states = loadPagedSnapshot { page, count ->
                remoteUserSyncDataSource.getWordStates(
                    bookId = book.id,
                    page = page,
                    count = count
                )
            }
            studySnapshotLocalStatePort.overwriteLearningStatesForBookFromRemote(
                book.id,
                states.map { it.toDomain(book.id) }
            )
            wordBookSnapshotLocalStatePort.overwriteLearningStatesForBookFromRemote(
                book.id,
                states.map { it.toSnapshot(book.id) }
            )
        }

        wordBookSnapshotLocalStatePort.overwriteProgressFromRemote(
            remoteUserSyncDataSource.getWordBookProgressList().getOrThrow().map { it.toDomain() }
        )

        studySnapshotLocalStatePort.overwriteFavoritesFromRemote(
            loadPagedSnapshot { page, count ->
                remoteUserSyncDataSource.getFavorites(page = page, count = count)
            }.map { it.toDomain() }
        )
        studySnapshotLocalStatePort.overwriteStudyRecordsFromRemote(
            loadPagedSnapshot { page, count ->
                remoteUserSyncDataSource.getStudyRecords(page = page, count = count)
            }.map { it.toDomain() }
        )
        studySnapshotLocalStatePort.overwriteDailyDurationsFromRemote(
            loadPagedSnapshot { page, count ->
                remoteUserSyncDataSource.getDailyStudyDurations(page = page, count = count)
            }
                .map { it.toSnapshot() }
        )
        studySnapshotLocalStatePort.overwriteCheckInRecordsFromRemote(
            loadPagedSnapshot { page, count ->
                remoteUserSyncDataSource.getCheckInRecords(page = page, count = count)
            }.map { it.toDomain() }
        )

        practiceSnapshotLocalStatePort.overwriteDurationsFromRemote(
            loadPagedSnapshot { page, count ->
                remoteLearningSyncDataSource.getPracticeDurations(page = page, count = count)
            }
                .map { it.toSnapshot() }
        )
        practiceSnapshotLocalStatePort.overwriteSessionsFromRemote(
            loadPagedSnapshot { page, count ->
                remoteLearningSyncDataSource.getPracticeSessions(page = page, count = count)
            }
                .map { it.toDomain() }
        )

        floatingSnapshotLocalStatePort.overwriteDisplayRecordsFromRemote(
            loadPagedSnapshot { page, count ->
                remoteLearningSyncDataSource.getFloatingDisplayRecords(page = page, count = count)
            }
                .map { it.toDomain() }
        )

        currentWordBookLocalStatePort.overwriteFromRemote(selectedBookId)
    }

    private suspend fun <T> loadPagedSnapshot(
        pageSize: Int = DEFAULT_PAGE_SIZE,
        loader: suspend (page: Int, count: Int) -> Result<com.chen.memorizewords.core.network.http.PageData<T>>
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

private fun com.chen.memorizewords.data.sync.remoteapi.dto.wordbook.WordBookDto.toDomain(): WordBook {
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

private fun com.chen.memorizewords.data.sync.remoteapi.dto.wordstate.WordStateDto.toDomain(
    fallbackBookId: Long
): WordLearningState {
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

private fun com.chen.memorizewords.data.sync.remoteapi.dto.wordstate.WordStateDto.toSnapshot(
    fallbackBookId: Long
): WordBookLearningStateSnapshot {
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

private fun com.chen.memorizewords.data.sync.remoteapi.api.datasync.FavoriteDto.toDomain(): WordFavorites {
    return WordFavorites(
        wordId = wordId,
        word = word,
        definitions = definitions,
        phonetic = phonetic,
        addedDate = addedDate
    )
}

private fun com.chen.memorizewords.data.sync.remoteapi.api.datasync.WordBookProgressDto.toDomain():
    WordBookProgress {
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

private fun com.chen.memorizewords.data.sync.remoteapi.api.datasync.StudyRecordDto.toDomain():
    DailyStudyRecords {
    return DailyStudyRecords(
        date = date,
        wordId = wordId,
        word = word,
        definition = definition,
        isNewWord = isNewWord
    )
}

private fun com.chen.memorizewords.data.sync.remoteapi.api.datasync.DailyStudyDurationDto.toSnapshot():
    StudyDailyDurationSnapshot {
    return StudyDailyDurationSnapshot(
        date = date,
        totalDurationMs = totalDurationMs,
        updatedAt = updatedAt,
        isNewPlanCompleted = isNewPlanCompleted,
        isReviewPlanCompleted = isReviewPlanCompleted
    )
}

private fun com.chen.memorizewords.data.sync.remoteapi.api.datasync.CheckInRecordDto.toDomain():
    CheckInRecord {
    return CheckInRecord(
        date = date,
        type = runCatching { CheckInType.valueOf(type) }.getOrDefault(CheckInType.AUTO),
        signedAt = signedAt,
        updatedAt = updatedAt
    )
}

private fun com.chen.memorizewords.data.sync.remoteapi.api.learningsync.PracticeDurationDto.toSnapshot():
    PracticeDailyDurationSnapshot {
    return PracticeDailyDurationSnapshot(
        date = date,
        totalDurationMs = totalDurationMs,
        updatedAt = updatedAt
    )
}

private fun com.chen.memorizewords.data.sync.remoteapi.api.learningsync.PracticeSessionDto.toDomain():
    PracticeSessionRecord {
    return PracticeSessionRecord(
        id = id,
        date = date,
        mode = runCatching {
            com.chen.memorizewords.domain.practice.PracticeMode.valueOf(mode)
        }.getOrDefault(com.chen.memorizewords.domain.practice.PracticeMode.LISTENING),
        entryType = runCatching {
            com.chen.memorizewords.domain.practice.PracticeEntryType.valueOf(entryType)
        }.getOrDefault(com.chen.memorizewords.domain.practice.PracticeEntryType.SELF),
        entryCount = entryCount,
        durationMs = durationMs,
        createdAt = createdAt,
        wordIds = wordIds,
        questionCount = questionCount,
        completedCount = completedCount,
        correctCount = correctCount,
        submitCount = submitCount
    )
}

private fun com.chen.memorizewords.data.sync.remoteapi.api.learningsync.FloatingDisplayRecordDto.toDomain():
    FloatingWordDisplayRecord {
    return FloatingWordDisplayRecord(
        date = date,
        displayCount = displayCount,
        wordIds = wordIds,
        updatedAt = updatedAt
    )
}

private const val DEFAULT_PAGE_SIZE = 100
