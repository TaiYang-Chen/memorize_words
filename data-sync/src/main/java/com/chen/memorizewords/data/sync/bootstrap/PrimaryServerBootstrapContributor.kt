package com.chen.memorizewords.data.sync.bootstrap

import android.util.Log
import com.chen.memorizewords.core.common.calendar.CheckInConfigDataSource
import com.chen.memorizewords.data.sync.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.sync.remote.learningsync.RemoteLearningSyncDataSource
import com.chen.memorizewords.data.sync.remoteapi.dto.wordbook.WordBookDto
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
import com.chen.memorizewords.domain.wordbook.repository.WordBookContentReadinessPort
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
    private val wordBookContentReadinessPort: WordBookContentReadinessPort,
    private val checkInConfigDataSource: CheckInConfigDataSource,
    private val errorLogger: PostLoginBootstrapErrorLogger
) : ServerBootstrapContributor {

    override val bootstrapKey: String = "primary_server_snapshot"

    override suspend fun bootstrapFromServer(): Result<Unit> = runCatching {
        val onboardingSnapshot = bootstrapStep("getOnboardingState") {
            remoteUserSyncDataSource.getOnboardingState().getOrThrow()
        }
        bootstrapStep("replaceOnboardingSnapshot") {
            onboardingRepository.replaceCurrentSnapshot(onboardingSnapshot)
        }

        val studyPlan = bootstrapStep("getStudyPlan") {
            remoteUserSyncDataSource.getStudyPlan().getOrThrow()
        }
        bootstrapStep("overwriteStudyPlan") {
            studyPlanLocalStatePort.overwriteFromRemote(studyPlan)
        }

        val practiceSettings = bootstrapStep("getPracticeSettings") {
            remoteLearningSyncDataSource.getPracticeSettings().getOrThrow()
        }
        bootstrapStep("overwritePracticeSettings") {
            practiceSettings?.let {
                practiceSettingsLocalStatePort.overwriteFromRemote(it)
            } ?: practiceSettingsLocalStatePort.clearLocalState()
        }

        val floatingSettings = bootstrapStep("getFloatingSettings") {
            remoteLearningSyncDataSource.getFloatingSettings().getOrThrow()
        }
        bootstrapStep("overwriteFloatingSettings") {
            floatingSettings?.let {
                floatingSettingsLocalStatePort.overwriteFromRemote(it)
            } ?: floatingSettingsLocalStatePort.clearLocalState()
        }

        val checkInConfig = bootstrapStep("getCheckInConfig") {
            remoteUserSyncDataSource.getCheckInConfig().getOrThrow()
        }
        bootstrapStep("overwriteCheckInConfig") {
            checkInConfig?.let { config ->
                checkInConfigDataSource.saveDayBoundaryOffsetMinutes(config.dayBoundaryOffsetMinutes)
                checkInConfigDataSource.saveTimezoneId(config.timezoneId)
            }
        }

        val checkInStatus = bootstrapStep("getCheckInStatus") {
            remoteUserSyncDataSource.getCheckInStatus().getOrThrow()
        }
        bootstrapStep("overwriteCheckInStatus") {
            checkInStatus?.let { status ->
                checkInConfigDataSource.saveCachedMakeupCardBalance(status.makeupCardBalance)
                checkInConfigDataSource.saveLastCheckInSyncAt(System.currentTimeMillis())
            }
        }

        val remoteBooks = bootstrapStep("getMyWordBooks") {
            remoteUserSyncDataSource.getMyWordBooks().getOrThrow()
        }
        val selectedBookId = remoteBooks.firstOrNull { it.isSelected }?.id
            ?: onboardingSnapshot?.selectedWordBookId

        remoteBooks.forEach { dto ->
            bootstrapStep("ensureBookContentReady(bookId=${dto.id})") {
                wordBookContentReadinessPort.ensureContentReady(
                    book = dto.toDomain(),
                    forceRefresh = false
                )
            }
        }
        val restoredSelectedBookId = bootstrapStep("restoreCurrentWordBookSelection") {
            restoreCurrentWordBookSelection(
                selectedBookId = selectedBookId,
                remoteBooks = remoteBooks
            )
        }

        remoteBooks.forEach { book ->
            val states = bootstrapStep("getWordStates(bookId=${book.id})") {
                loadPagedSnapshot { page, count ->
                    remoteUserSyncDataSource.getWordStates(
                        bookId = book.id,
                        page = page,
                        count = count
                    )
                }
            }
            bootstrapStep("overwriteWordStates(bookId=${book.id})") {
                studySnapshotLocalStatePort.overwriteLearningStatesForBookFromRemote(
                    book.id,
                    states.map { it.toDomain(book.id) }
                )
                wordBookSnapshotLocalStatePort.overwriteLearningStatesForBookFromRemote(
                    book.id,
                    states.map { it.toSnapshot(book.id) }
                )
            }
        }

        val wordBookProgressList = bootstrapStep("getWordBookProgressList") {
            remoteUserSyncDataSource.getWordBookProgressList().getOrThrow()
        }
        bootstrapStep("overwriteWordBookProgress") {
            wordBookSnapshotLocalStatePort.overwriteProgressFromRemote(
                wordBookProgressList.map { it.toDomain() }
            )
        }

        val favorites = bootstrapStep("getFavorites") {
            loadPagedSnapshot { page, count ->
                remoteUserSyncDataSource.getFavorites(page = page, count = count)
            }
        }
        bootstrapStep("overwriteFavorites") {
            studySnapshotLocalStatePort.overwriteFavoritesFromRemote(favorites.map { it.toDomain() })
        }
        val studyRecords = bootstrapStep("getStudyRecords") {
            loadPagedSnapshot { page, count ->
                remoteUserSyncDataSource.getStudyRecords(page = page, count = count)
            }
        }
        bootstrapStep("overwriteStudyRecords") {
            studySnapshotLocalStatePort.overwriteStudyRecordsFromRemote(
                studyRecords.map { it.toDomain() }
            )
        }
        val dailyStudyDurations = bootstrapStep("getDailyStudyDurations") {
            loadPagedSnapshot { page, count ->
                remoteUserSyncDataSource.getDailyStudyDurations(page = page, count = count)
            }
        }
        bootstrapStep("overwriteDailyStudyDurations") {
            studySnapshotLocalStatePort.overwriteDailyDurationsFromRemote(
                dailyStudyDurations.map { it.toSnapshot() }
            )
        }
        val checkInRecords = bootstrapStep("getCheckInRecords") {
            loadPagedSnapshot { page, count ->
                remoteUserSyncDataSource.getCheckInRecords(page = page, count = count)
            }
        }
        bootstrapStep("overwriteCheckInRecords") {
            studySnapshotLocalStatePort.overwriteCheckInRecordsFromRemote(
                checkInRecords.map { it.toDomain() }
            )
        }

        val practiceDurations = bootstrapStep("getPracticeDurations") {
            loadPagedSnapshot { page, count ->
                remoteLearningSyncDataSource.getPracticeDurations(page = page, count = count)
            }
        }
        bootstrapStep("overwritePracticeDurations") {
            practiceSnapshotLocalStatePort.overwriteDurationsFromRemote(
                practiceDurations.map { it.toSnapshot() }
            )
        }
        val practiceSessions = bootstrapStep("getPracticeSessions") {
            loadPagedSnapshot { page, count ->
                remoteLearningSyncDataSource.getPracticeSessions(page = page, count = count)
            }
        }
        bootstrapStep("overwritePracticeSessions") {
            practiceSnapshotLocalStatePort.overwriteSessionsFromRemote(
                practiceSessions.map { it.toDomain() }
            )
        }

        val floatingDisplayRecords = bootstrapStep("getFloatingDisplayRecords") {
            loadPagedSnapshot { page, count ->
                remoteLearningSyncDataSource.getFloatingDisplayRecords(page = page, count = count)
            }
        }
        bootstrapStep("overwriteFloatingDisplayRecords") {
            floatingSnapshotLocalStatePort.overwriteDisplayRecordsFromRemote(
                floatingDisplayRecords.map { it.toDomain() }
            )
        }

        bootstrapStep("overwriteCurrentWordBook") {
            currentWordBookLocalStatePort.overwriteFromRemote(restoredSelectedBookId)
        }
    }

    private suspend fun <T> bootstrapStep(
        name: String,
        block: suspend () -> T
    ): T {
        return try {
            block()
        } catch (throwable: Throwable) {
            Log.e(TAG, "Post-login bootstrap step failed: $name", throwable)
            errorLogger.logFailure(
                source = "PrimaryServerBootstrapContributor",
                stepName = name,
                throwable = throwable
            )
            throw ServerBootstrapStepException(stepName = name, cause = throwable)
        }
    }

    private suspend fun restoreCurrentWordBookSelection(
        selectedBookId: Long?,
        remoteBooks: List<WordBookDto>
    ): Long? {
        val safeSelectedBookId = selectedBookId?.takeIf { it > 0L } ?: return null
        if (remoteBooks.any { it.id == safeSelectedBookId }) {
            return safeSelectedBookId
        }

        val selectedBook = remoteWordBookRepository.getShopBookById(safeSelectedBookId)
            ?: return null
        bootstrapStep("ensureBookContentReady(bookId=$safeSelectedBookId)") {
            wordBookContentReadinessPort.ensureContentReady(
                book = selectedBook,
                forceRefresh = false
            )
        }
        return safeSelectedBookId
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

class ServerBootstrapStepException(
    val stepName: String,
    cause: Throwable
) : Exception(
    "Post-login bootstrap step failed: $stepName: ${cause.message ?: cause::class.java.simpleName}",
    cause
)

private const val TAG = "ServerBootstrap"

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
